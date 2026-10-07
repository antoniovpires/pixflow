package com.pixflow.pixkey;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.security.CurrentUser;
import com.pixflow.security.SecurityConfig;
import com.pixflow.user.User;
import com.pixflow.web.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PixKeyController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class PixKeyControllerTests {

    private static final UUID ACCOUNT_ID = UUID.fromString("7a2a2c2e-3b69-4c9f-8d7e-2a4b7c2e3d4e");
    private static final UUID PIX_KEY_ID = UUID.fromString("9c4c4e4a-5d81-4e1b-8f90-4c6d9e4a5f6b");

    private static final UUID USER_ID = UUID.fromString("6f1f1b1e-2a58-4b8e-9c8e-1f3f6a1d2b3c");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUser currentUser;

    @BeforeEach
    void authenticate() {
        when(currentUser.accountId(any())).thenReturn(ACCOUNT_ID);
    }


    @MockitoBean
    private PixKeyService pixKeyService;

    @Test
    void lists_pix_keys() throws Exception {
        when(pixKeyService.getAllForAccount(ACCOUNT_ID)).thenReturn(List.of(pixKey(PIX_KEY_ID, "ada@pixflow.test", KeyType.EMAIL)));

        mvc.perform(get("/pixkeys").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(PIX_KEY_ID.toString()))
                .andExpect(jsonPath("$[0].accountId").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[0].keyValue").value("ada@pixflow.test"))
                .andExpect(jsonPath("$[0].keyType").value("EMAIL"));
    }

    @Test
    void returns_one_pix_key() throws Exception {
        when(pixKeyService.getOwned(PIX_KEY_ID, ACCOUNT_ID)).thenReturn(pixKey(PIX_KEY_ID, "11988887777", KeyType.PHONE));

        mvc.perform(get("/pixkeys/{id}", PIX_KEY_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keyValue").value("11988887777"))
                .andExpect(jsonPath("$.keyType").value("PHONE"));
    }

    @Test
    void returns_404_when_the_pix_key_does_not_exist() throws Exception {
        when(pixKeyService.getOwned(PIX_KEY_ID, ACCOUNT_ID)).thenThrow(new PixKeyNotFoundException("Pix key not found"));

        mvc.perform(get("/pixkeys/{id}", PIX_KEY_ID).with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Pix key not found"));
    }

    @Test
    void returns_201_and_passes_the_raw_key_to_the_service() throws Exception {
        when(pixKeyService.create(ACCOUNT_ID, "Ada@Pixflow.test", KeyType.EMAIL))
                .thenReturn(pixKey(PIX_KEY_ID, "ada@pixflow.test", KeyType.EMAIL));

        mvc.perform(post("/pixkeys").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "Ada@Pixflow.test", "keyType": "EMAIL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.keyValue").value("ada@pixflow.test"))
                .andExpect(jsonPath("$.keyType").value("EMAIL"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID.toString()));

        verify(pixKeyService).create(ACCOUNT_ID, "Ada@Pixflow.test", KeyType.EMAIL);
    }

    @Test
    void returns_400_when_fields_are_missing() throws Exception {
        mvc.perform(post("/pixkeys").with(auth()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.keyValue").exists())
                .andExpect(jsonPath("$.errors.keyType").exists());

        verifyNoInteractions(pixKeyService);
    }

    @Test
    void returns_400_for_malformed_json() throws Exception {
        mvc.perform(post("/pixkeys").with(auth()).contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns_400_for_an_unknown_key_type() throws Exception {
        mvc.perform(post("/pixkeys").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "ada@pixflow.test", "keyType": "EVP"}
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(pixKeyService);
    }

    @Test
    void returns_404_when_the_account_does_not_exist() throws Exception {
        when(pixKeyService.create(ACCOUNT_ID, "ada@pixflow.test", KeyType.EMAIL))
                .thenThrow(new AccountNotFoundException("Account not found"));

        mvc.perform(post("/pixkeys").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "ada@pixflow.test", "keyType": "EMAIL"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void returns_409_when_the_key_already_exists() throws Exception {
        when(pixKeyService.create(ACCOUNT_ID, "ada@pixflow.test", KeyType.EMAIL))
                .thenThrow(new PixKeyAlreadyExistsException("Pix key already exists"));

        mvc.perform(post("/pixkeys").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyValue": "ada@pixflow.test", "keyType": "EMAIL"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Pix key already exists"));
    }

    @Test
    void returns_404_for_another_users_pix_key() throws Exception {
        when(pixKeyService.getOwned(PIX_KEY_ID, ACCOUNT_ID)).thenThrow(new PixKeyNotFoundException("Pix key not found"));

        mvc.perform(get("/pixkeys/{id}", PIX_KEY_ID).with(auth())).andExpect(status().isNotFound());
    }

    @Test
    void ignores_an_account_id_sent_in_the_body() throws Exception {
        when(pixKeyService.create(ACCOUNT_ID, "ada@pixflow.test", KeyType.EMAIL))
                .thenReturn(pixKey(PIX_KEY_ID, "ada@pixflow.test", KeyType.EMAIL));

        mvc.perform(post("/pixkeys").with(auth())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId": "%s", "keyValue": "ada@pixflow.test", "keyType": "EMAIL"}
                                """.formatted(UUID.randomUUID())))
                .andExpect(status().isCreated());

        verify(pixKeyService).create(ACCOUNT_ID, "ada@pixflow.test", KeyType.EMAIL);
    }

    @Test
    void returns_401_without_a_token() throws Exception {
        mvc.perform(get("/pixkeys")).andExpect(status().isUnauthorized());
        mvc.perform(post("/pixkeys").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(delete("/pixkeys/{id}", PIX_KEY_ID)).andExpect(status().isUnauthorized());

        verifyNoInteractions(pixKeyService);
    }

    @Test
    void deletes_a_pix_key() throws Exception {
        mvc.perform(delete("/pixkeys/{id}", PIX_KEY_ID).with(auth())).andExpect(status().isNoContent());

        verify(pixKeyService).deleteOwned(PIX_KEY_ID, ACCOUNT_ID);
    }

    @Test
    void returns_404_when_deleting_a_missing_pix_key() throws Exception {
        doThrow(new PixKeyNotFoundException("Pix key not found")).when(pixKeyService).deleteOwned(PIX_KEY_ID, ACCOUNT_ID);

        mvc.perform(delete("/pixkeys/{id}", PIX_KEY_ID).with(auth())).andExpect(status().isNotFound());
    }

    @Test
    void returns_409_when_a_transfer_still_references_the_key() throws Exception {
        doThrow(new PixKeyInUseException("Pix key is still referenced")).when(pixKeyService).deleteOwned(PIX_KEY_ID, ACCOUNT_ID);

        mvc.perform(delete("/pixkeys/{id}", PIX_KEY_ID).with(auth()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Pix key is still referenced"));
    }

    private PixKey pixKey(UUID id, String keyValue, KeyType keyType) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", ACCOUNT_ID);
        account.setUser(user);
        PixKey pixKey = new PixKey(account, keyValue, keyType);
        ReflectionTestUtils.setField(pixKey, "id", id);
        return pixKey;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }
}
