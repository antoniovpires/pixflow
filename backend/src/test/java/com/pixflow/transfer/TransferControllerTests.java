package com.pixflow.transfer;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.InsufficientBalanceException;
import com.pixflow.pixkey.PixKeyNotFoundException;
import com.pixflow.security.SecurityConfig;
import com.pixflow.web.ApiExceptionHandler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Web slice: only the controller + advice + security config are loaded.
// The service is a Mockito mock, so no database is involved.
@WebMvcTest(TransferController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class TransferControllerTests {

    private static final String VALID_BODY = """
            {"sourceAccountId": "6f1f1b1e-2a58-4b8e-9c8e-1f3f6a1d2b3c",
             "pixKeyValue": "bruno@pixflow.test",
             "amount": 50.00}
            """;

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private TransferRetryService transferService;

    private org.springframework.test.web.servlet.ResultActions postTransfer(String body) throws Exception {
        return mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(body));
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
                .andExpect(jsonPath("$.errors.sourceAccountId").exists())
                .andExpect(jsonPath("$.errors.pixKeyValue").exists())
                .andExpect(jsonPath("$.errors.amount").exists());
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
}
