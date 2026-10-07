package com.pixflow.account;

import com.pixflow.ledgerentry.Direction;
import com.pixflow.ledgerentry.LedgerEntry;
import com.pixflow.pixkey.KeyType;
import com.pixflow.pixkey.PixKey;
import com.pixflow.transfer.Transfer;
import com.pixflow.user.User;
import com.pixflow.user.UserNotFoundException;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

@DataJpaTest
@Import(AccountService.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private AccountService accounts;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void create_starts_at_zero_balance_in_brl() {
        User user = persistUser("ada@pixflow.test");

        Account created = accounts.create(user.getId());
        em.flush();
        em.clear();

        Account stored = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("0.00");
        assertThat(stored.getCurrency()).isEqualTo("BRL");
        assertThat(stored.getUser().getId()).isEqualTo(user.getId());
    }

    @Test
    void create_rejects_an_unknown_user() {
        assertThatExceptionOfType(UserNotFoundException.class)
                .isThrownBy(() -> accounts.create(UUID.randomUUID()));
    }

    @Test
    void update_changes_the_owner_and_leaves_the_balance() {
        User ada = persistUser("ada@pixflow.test");
        User grace = persistUser("grace@pixflow.test");
        Account created = accounts.create(ada.getId());
        created.credit(new BigDecimal("40.00"));
        em.flush();
        em.clear();

        accounts.update(created.getId(), grace.getId());
        em.flush();
        em.clear();

        Account stored = accountRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getUser().getId()).isEqualTo(grace.getId());
        assertThat(stored.getBalance()).isEqualByComparingTo("40.00");
    }

    @Test
    void delete_removes_a_zero_balance_account() {
        User user = persistUser("ada@pixflow.test");
        Account created = accounts.create(user.getId());

        accounts.delete(created.getId());

        assertThat(accountRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void delete_rejects_a_non_zero_balance() {
        User user = persistUser("ada@pixflow.test");
        Account created = accounts.create(user.getId());
        created.credit(new BigDecimal("10.00"));
        em.flush();

        assertThatExceptionOfType(InvalidAccountException.class)
                .isThrownBy(() -> accounts.delete(created.getId()));
        assertThat(accountRepository.findById(created.getId())).isPresent();
        assertThat(accountRepository.findById(created.getId()).orElseThrow().getBalance())
                .isEqualByComparingTo("10.00");
    }

    @Test
    void delete_rejects_an_account_that_still_has_a_pix_key() {
        User user = persistUser("ada@pixflow.test");
        Account created = accounts.create(user.getId());
        em.persist(new PixKey(created, "11988887777", KeyType.PHONE));
        em.flush();
        em.clear();

        assertThatExceptionOfType(AccountInUseException.class)
                .isThrownBy(() -> accounts.delete(created.getId()));
    }

    @Test
    void ledger_returns_only_that_accounts_entry() {
        Account source = accounts.create(persistUser("ada@pixflow.test").getId());
        source.credit(new BigDecimal("100.00"));
        Account target = accounts.create(persistUser("grace@pixflow.test").getId());
        em.flush();

        PixKey pixKey = new PixKey(target, "11988887777", KeyType.PHONE);
        em.persist(pixKey);
        Transfer transfer = new Transfer(source, target, pixKey, new BigDecimal("40.00"), "ledger-1");
        em.persist(transfer);
        em.persist(new LedgerEntry(transfer, source, new BigDecimal("40.00"), Direction.DEBIT));
        em.persist(new LedgerEntry(transfer, target, new BigDecimal("40.00"), Direction.CREDIT));
        em.flush();
        em.clear();

        List<LedgerEntry> sourceLedger = accounts.getLedger(source.getId());
        assertThat(sourceLedger).singleElement().satisfies(entry -> {
            assertThat(entry.getDirection()).isEqualTo(Direction.DEBIT);
            assertThat(entry.getAmount()).isEqualByComparingTo("40.00");
            assertThat(entry.getAccount().getId()).isEqualTo(source.getId());
            assertThat(entry.getTransfer().getId()).isEqualTo(transfer.getId());
        });

        List<LedgerEntry> targetLedger = accounts.getLedger(target.getId());
        assertThat(targetLedger).singleElement().satisfies(entry -> {
            assertThat(entry.getDirection()).isEqualTo(Direction.CREDIT);
            assertThat(entry.getAmount()).isEqualByComparingTo("40.00");
            assertThat(entry.getAccount().getId()).isEqualTo(target.getId());
        });
    }

    @Test
    void ledger_is_empty_when_the_account_has_no_entries() {
        Account account = accounts.create(persistUser("ada@pixflow.test").getId());

        assertThat(accounts.getLedger(account.getId())).isEmpty();
    }

    @Test
    void ledger_rejects_an_unknown_account() {
        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> accounts.getLedger(UUID.randomUUID()));
    }

    private User persistUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        em.persist(user);
        em.flush();
        return user;
    }
}
