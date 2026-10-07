package com.pixflow.account;

import com.pixflow.ledgerentry.Direction;
import com.pixflow.ledgerentry.LedgerEntry;
import com.pixflow.security.CurrentUser;
import com.pixflow.security.SecurityConfig;
import com.pixflow.transfer.Transfer;
import com.pixflow.user.User;
import com.pixflow.web.ApiExceptionHandler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AccountController.class)
@Import({SecurityConfig.class, ApiExceptionHandler.class})
class AccountControllerTests {

    private static final UUID USER_ID = UUID.fromString("6f1f1b1e-2a58-4b8e-9c8e-1f3f6a1d2b3c");
    private static final UUID ACCOUNT_ID = UUID.fromString("7a2a2c2e-3b69-4c9f-8d7e-2a4b7c2e3d4e");
    private static final UUID TRANSFER_ID = UUID.fromString("1c5c5e5a-6e92-4f2c-9a01-5d7e0f5b6a7c");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUser currentUser;

    @BeforeEach
    void authenticate() {
        when(currentUser.accountId(any())).thenReturn(ACCOUNT_ID);
    }


    @MockitoBean
    private AccountService accountService;

    @Test
    void lists_accounts() throws Exception {
        when(accountService.getById(ACCOUNT_ID)).thenReturn(account(ACCOUNT_ID, USER_ID, "0"));

        mvc.perform(get("/accounts").with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$[0].userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$[0].balance").value(0))
                .andExpect(jsonPath("$[0].currency").value("BRL"));
    }

    @Test
    void returns_one_account() throws Exception {
        when(accountService.getById(ACCOUNT_ID)).thenReturn(account(ACCOUNT_ID, USER_ID, "25.50"));

        mvc.perform(get("/accounts/{id}", ACCOUNT_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ACCOUNT_ID.toString()))
                .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                .andExpect(jsonPath("$.balance").value(25.50))
                .andExpect(jsonPath("$.currency").value("BRL"));
    }

    @Test
    void returns_the_account_ledger() throws Exception {
        when(accountService.getLedger(ACCOUNT_ID))
                .thenReturn(List.of(ledgerEntry(new BigDecimal("40.00"), Direction.DEBIT)));

        mvc.perform(get("/accounts/{id}/ledger", ACCOUNT_ID).with(auth()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transferId").value(TRANSFER_ID.toString()))
                .andExpect(jsonPath("$[0].amount").value(40.00))
                .andExpect(jsonPath("$[0].direction").value("DEBIT"));
    }

    @Test
    void returns_404_when_the_ledger_account_does_not_exist() throws Exception {
        when(accountService.getLedger(ACCOUNT_ID)).thenThrow(new AccountNotFoundException("Account not found"));

        mvc.perform(get("/accounts/{id}/ledger", ACCOUNT_ID).with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void returns_404_when_the_account_does_not_exist() throws Exception {
        when(accountService.getById(ACCOUNT_ID)).thenThrow(new AccountNotFoundException("Account not found"));

        mvc.perform(get("/accounts/{id}", ACCOUNT_ID).with(auth()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Account not found"));
    }

    @Test
    void returns_404_for_another_users_account() throws Exception {
        UUID someoneElsesAccount = UUID.randomUUID();

        mvc.perform(get("/accounts/{id}", someoneElsesAccount).with(auth()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    @Test
    void returns_404_for_another_users_ledger() throws Exception {
        mvc.perform(get("/accounts/{id}/ledger", UUID.randomUUID()).with(auth()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    @Test
    void cannot_delete_another_users_account() throws Exception {
        mvc.perform(delete("/accounts/{id}", UUID.randomUUID()).with(auth()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(accountService);
    }

    @Test
    void returns_401_without_a_token() throws Exception {
        mvc.perform(get("/accounts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/accounts/{id}", ACCOUNT_ID)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/accounts/{id}", ACCOUNT_ID)).andExpect(status().isUnauthorized());

        verifyNoInteractions(accountService);
    }

    @Test
    void deletes_an_account() throws Exception {
        mvc.perform(delete("/accounts/{id}", ACCOUNT_ID).with(auth())).andExpect(status().isNoContent());

        verify(accountService).delete(ACCOUNT_ID);
    }

    @Test
    void returns_422_when_deleting_an_account_with_a_balance() throws Exception {
        doThrow(new InvalidAccountException("Account balance must be zero"))
                .when(accountService).delete(ACCOUNT_ID);

        mvc.perform(delete("/accounts/{id}", ACCOUNT_ID).with(auth()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Account balance must be zero"));
    }

    @Test
    void returns_409_when_the_account_is_still_referenced() throws Exception {
        doThrow(new AccountInUseException("Account is still referenced"))
                .when(accountService).delete(ACCOUNT_ID);

        mvc.perform(delete("/accounts/{id}", ACCOUNT_ID).with(auth())).andExpect(status().isConflict());
    }

    private Account account(UUID accountId, UUID userId, String balance) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", userId);
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", accountId);
        account.setUser(user);
        if (new BigDecimal(balance).compareTo(BigDecimal.ZERO) > 0) {
            account.credit(new BigDecimal(balance));
        }
        return account;
    }

    private LedgerEntry ledgerEntry(BigDecimal amount, Direction direction) {
        Account account = account(ACCOUNT_ID, USER_ID, "0");
        Transfer transfer = new Transfer(account, account, null, amount, "1");
        ReflectionTestUtils.setField(transfer, "id", TRANSFER_ID);
        LedgerEntry entry = new LedgerEntry(transfer, account, amount, direction);
        ReflectionTestUtils.setField(entry, "id", UUID.randomUUID());
        return entry;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor auth() {
        return jwt().jwt(token -> token.subject(USER_ID.toString()));
    }
}
