package com.pixflow.auth;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtEncoder jwtEncoder;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE ledger_entries, transfers, pixkeys, accounts, users CASCADE");
    }

    private String json(String template, Object... args) {
        return template.formatted(args);
    }

    private ResultActions register(String email, String password) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"name": "Test", "email": "%s", "password": "%s"}
                        """, email, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json("""
                        {"email": "%s", "password": "%s"}
                        """, email, password)));
    }

    private record Person(String userId, String accountId, String token) {}

    private Person registerAndLogin(String email) throws Exception {
        String registered = register(email, "correct-horse").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(login(email, "correct-horse").andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(), "$.accessToken");
        return new Person(JsonPath.read(registered, "$.userId"), JsonPath.read(registered, "$.accountId"), token);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    @Test
    void register_creates_a_user_with_exactly_one_empty_account_and_never_stores_the_plain_password() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");

        mvc.perform(get("/accounts").header("Authorization", bearer(ada.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ada.accountId()))
                .andExpect(jsonPath("$[0].balance").value(0));

        String stored = jdbc.queryForObject("SELECT password_hash FROM users", String.class);
        assertThat(stored).startsWith("$2").isNotEqualTo("correct-horse");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM accounts", Integer.class)).isEqualTo(1);
    }

    @Test
    void login_returns_a_short_lived_bearer_token() throws Exception {
        register("ada@pixflow.test", "correct-horse").andExpect(status().isCreated());

        login("ada@pixflow.test", "correct-horse")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(15 * 60));
    }

    @Test
    void login_is_case_insensitive_on_email() throws Exception {
        register("Ada@Pixflow.test", "correct-horse").andExpect(status().isCreated());

        login("ADA@pixflow.TEST", "correct-horse").andExpect(status().isOk());
    }

    @Test
    void wrong_password_and_unknown_email_get_the_same_401() throws Exception {
        register("ada@pixflow.test", "correct-horse").andExpect(status().isCreated());

        String wrongPassword = login("ada@pixflow.test", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownEmail = login("nobody@pixflow.test", "wrong-password")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(JsonPath.<String>read(wrongPassword, "$.detail"))
                .isEqualTo(JsonPath.<String>read(unknownEmail, "$.detail"));
    }

    @Test
    void duplicate_email_is_a_409_even_with_different_casing() throws Exception {
        register("ada@pixflow.test", "correct-horse").andExpect(status().isCreated());

        register("ADA@pixflow.test", "another-password").andExpect(status().isConflict());
    }

    @Test
    void register_validates_the_input() throws Exception {
        register("not-an-email", "correct-horse").andExpect(status().isBadRequest());
        register("ada@pixflow.test", "short").andExpect(status().isBadRequest());
    }

    @Test
    void protected_endpoints_return_401_without_a_token() throws Exception {
        mvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/transfers")).andExpect(status().isUnauthorized());
        mvc.perform(get("/pixkeys")).andExpect(status().isUnauthorized());
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void a_user_cannot_read_another_users_account_ledger_or_pix_key() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");
        Person bruno = registerAndLogin("bruno@pixflow.test");
        String brunoKeyId = JsonPath.read(mvc.perform(post("/pixkeys").header("Authorization", bearer(bruno.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "bruno@pixflow.test", "keyType": "EMAIL"}
                                """))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");

        mvc.perform(get("/accounts/{id}", bruno.accountId()).header("Authorization", bearer(ada.token())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/accounts/{id}/ledger", bruno.accountId()).header("Authorization", bearer(ada.token())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/pixkeys/{id}", brunoKeyId).header("Authorization", bearer(ada.token())))
                .andExpect(status().isNotFound());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/pixkeys/{id}", brunoKeyId).header("Authorization", bearer(ada.token())))
                .andExpect(status().isNotFound());

        mvc.perform(get("/pixkeys").header("Authorization", bearer(ada.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void a_transfer_debits_the_callers_account_taken_from_the_token() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");
        Person bruno = registerAndLogin("bruno@pixflow.test");
        jdbc.update("UPDATE accounts SET balance = 100.00 WHERE id = ?::uuid", ada.accountId());
        mvc.perform(post("/pixkeys").header("Authorization", bearer(bruno.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "bruno@pixflow.test", "keyType": "EMAIL"}
                                """))
                .andExpect(status().isCreated());

        mvc.perform(post("/transfers").header("Authorization", bearer(ada.token()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json("""
                                {"sourceAccountId": "%s", "pixKeyValue": "bruno@pixflow.test",
                                 "amount": 40.00, "idempotencyKey": "k-1"}
                                """, bruno.accountId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceAccountId").value(ada.accountId()))
                .andExpect(jsonPath("$.targetAccountId").value(bruno.accountId()));

        mvc.perform(get("/accounts/{id}", ada.accountId()).header("Authorization", bearer(ada.token())))
                .andExpect(jsonPath("$.balance").value(60.00));
        mvc.perform(get("/accounts/{id}", bruno.accountId()).header("Authorization", bearer(bruno.token())))
                .andExpect(jsonPath("$.balance").value(40.00));
        mvc.perform(get("/transfers").header("Authorization", bearer(bruno.token())))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/accounts/{id}/ledger", bruno.accountId()).header("Authorization", bearer(bruno.token())))
                .andExpect(jsonPath("$[0].direction").value("CREDIT"));
    }

    @Test
    void a_token_with_a_modified_payload_is_rejected() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");
        Person bruno = registerAndLogin("bruno@pixflow.test");

        String[] parts = ada.token().split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace(ada.userId(), bruno.userId());
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];

        mvc.perform(get("/accounts").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void an_expired_token_is_rejected() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");
        Instant twoHoursAgo = Instant.now().minusSeconds(7200);
        String expired = sign(jwtEncoder, ada.userId(), twoHoursAgo, twoHoursAgo.plusSeconds(900));

        mvc.perform(get("/accounts").header("Authorization", bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void a_token_signed_with_a_different_secret_is_rejected() throws Exception {
        Person ada = registerAndLogin("ada@pixflow.test");
        JwtEncoder attacker = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "attacker-secret-that-is-32-bytes-long!!".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        String forged = sign(attacker, ada.userId(), Instant.now(), Instant.now().plusSeconds(900));

        mvc.perform(get("/accounts").header("Authorization", bearer(forged)))
                .andExpect(status().isUnauthorized());
    }

    private static String sign(JwtEncoder encoder, String subject, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(subject).issuedAt(issuedAt).expiresAt(expiresAt).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
