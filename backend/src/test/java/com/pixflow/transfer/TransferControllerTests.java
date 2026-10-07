package com.pixflow.transfer;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.InsufficientBalanceException;
import com.pixflow.pixkey.PixKeyNotFoundException;
import com.pixflow.security.CurrentUser;
import com.pixflow.security.SecurityConfig;
import com.pixflow.web.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class TransferControllerTests {

    private static final UUID ACCOUNT_ID = UUID.fromString("7a2a2c2e-3b69-4c9f-8d7e-2a4b7c2e3d4e");

    private static final String VALID_BODY = """
            {"pixKeyValue": "bruno@pixflow.test",
             "amount": 50.00,
             "idempotencyKey": "key-1"}
            """;

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
    private TransferRetryService transferService;

    private org.springframework.test.web.servlet.ResultActions postTransfer(String body) throws Exception {
        return mvc.perform(post("/transfers").with(auth()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void returns_201_with_the_created_transfer() throws Exception {
        Transfer transfer = new Transfer(new Account(), new Account(), null, new BigDecimal("50.00"), "1");
        when(transferService.createTransfer(any(), any(), any(), any())).thenReturn(transfer);

        postTransfer(VALID_BODY)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.amount").value(50.00));
    }

    @Test
    void takes_the_source_account_from_the_token_not_the_body() throws Exception {
        Transfer transfer = new Transfer(new Account(), new Account(), null, new BigDecimal("50.00"), "key-1");
        when(transferService.createTransfer(any(), any(), any(), any())).thenReturn(transfer);

        postTransfer("""
                {"sourceAccountId": "%s", "pixKeyValue": "bruno@pixflow.test", "amount": 50.00, "idempotencyKey": "key-1"}
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isCreated());

        verify(transferService).createTransfer(ACCOUNT_ID, "bruno@pixflow.test", new BigDecimal("50.00"), "key-1");
    }

    @Test
    void lists_only_the_callers_transfers() throws Exception {
        Transfer transfer = new Transfer(new Account(), new Account(), null, new BigDecimal("50.00"), "key-1");
        when(transferService.getTransfersForAccount(ACCOUNT_ID)).thenReturn(List.of(transfer));

        mvc.perform(get("/transfers").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(50.00));
    }

    @Test
    void returns_401_without_a_token() throws Exception {
        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/transfers")).andExpect(status().isUnauthorized());

        verifyNoInteractions(transferService);
    }

    @Test
    void returns_401_for_a_malformed_token() throws Exception {
        mvc.perform(get("/transfers").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returns_400_when_amount_is_not_positive() throws Exception {
        postTransfer(VALID_BODY.replace("50.00", "-5"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists());

        verifyNoInteractions(transferService);
    }

    @Test
    void returns_400_when_fields_are_missing() throws Exception {
        postTransfer("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.pixKeyValue").exists())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.errors.idempotencyKey").exists());
    }

    @Test
    void returns_400_for_malformed_json() throws Exception {
        postTransfer("{not json").andExpect(status().isBadRequest());
    }

    @Test
    void returns_404_when_source_account_does_not_exist() throws Exception {
        when(transferService.createTransfer(any(), any(), any(), any()))
                .thenThrow(new AccountNotFoundException("Source account not found"));

        postTransfer(VALID_BODY)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Source account not found"));
    }

    @Test
    void returns_404_when_pix_key_does_not_exist() throws Exception {
        when(transferService.createTransfer(any(), any(), any(), any()))
                .thenThrow(new PixKeyNotFoundException("Pix key not found"));

        postTransfer(VALID_BODY).andExpect(status().isNotFound());
    }

    @Test
    void returns_422_on_insufficient_balance() throws Exception {
        when(transferService.createTransfer(any(), any(), any(), any()))
                .thenThrow(new InsufficientBalanceException("Insufficient balance"));

        postTransfer(VALID_BODY).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns_422_when_sending_to_own_account() throws Exception {
        when(transferService.createTransfer(any(), any(), any(), any()))
                .thenThrow(new InvalidTransferException("Source and target accounts cannot be the same"));

        postTransfer(VALID_BODY).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returns_409_when_retries_are_exhausted() throws Exception {
        when(transferService.createTransfer(any(), any(), any(), any()))
                .thenThrow(new TransferConflictException("gave up", new RuntimeException()));

        postTransfer(VALID_BODY).andExpect(status().isConflict());
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }
}
