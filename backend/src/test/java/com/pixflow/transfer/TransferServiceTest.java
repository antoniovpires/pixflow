package com.pixflow.transfer;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.AccountRepository;
import com.pixflow.account.InsufficientBalanceException;
import com.pixflow.ledgerentry.Direction;
import com.pixflow.ledgerentry.LedgerEntry;
import com.pixflow.ledgerentry.LedgerEntryRepository;
import com.pixflow.pixkey.KeyType;
import com.pixflow.pixkey.PixKey;
import com.pixflow.pixkey.PixKeyNotFoundException;
import com.pixflow.pixkey.PixKeyRepository;
import com.pixflow.user.User;

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
@Import(TransferService.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransferServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private TransferService transfers;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private PixKeyRepository pixKeys;

    @Autowired
    private LedgerEntryRepository ledgerEntries;

    @Autowired
    private TestEntityManager em;

    private Account arrangeAccount(String email, String balance) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        em.persist(user);

        Account account = new Account();
        account.setUser(user);
        account.credit(new BigDecimal(balance));
        em.persist(account);
        em.flush();
        em.clear();

        return accounts.findById(account.getId()).orElseThrow();
    }

    private PixKey arrangePixKey(Account account, String keyValue) {
        PixKey pixKey = new PixKey(account, keyValue, KeyType.PHONE);
        pixKeys.save(pixKey);
        em.flush();
        em.clear();

        return pixKeys.findById(pixKey.getId()).orElseThrow();
    }

    @Test
    void create() {
        Account source = arrangeAccount("ada@pixflow.test", "100.00");
        Account target = arrangeAccount("grace@pixflow.test", "20.00");
        PixKey pixKey = arrangePixKey(target, "11988887777");

        Transfer transfer = transfers.createTransfer(
                source.getId(), pixKey.getKeyValue(), new BigDecimal("40.00"), "1");
        em.flush();
        em.clear();

        Account storedSource = accounts.findById(source.getId()).orElseThrow();
        Account storedTarget = accounts.findById(target.getId()).orElseThrow();
        assertThat(storedSource.getBalance()).isEqualByComparingTo("60.00");
        assertThat(storedTarget.getBalance()).isEqualByComparingTo("60.00");

        Transfer stored = em.find(Transfer.class, transfer.getId());
        assertThat(stored.getAmount()).isEqualByComparingTo("40.00");
        assertThat(stored.getStatus()).isEqualTo(Status.SUCCESS);
        assertThat(stored.getSourceAccount().getId()).isEqualTo(source.getId());
        assertThat(stored.getTargetAccount().getId()).isEqualTo(target.getId());
        assertThat(stored.getPixKey().getKeyValue()).isEqualTo("11988887777");

        List<LedgerEntry> entries = ledgerEntries.findByTransferId(transfer.getId());
        assertThat(entries).hasSize(2);
        assertThat(entries)
                .filteredOn(entry -> entry.getDirection() == Direction.DEBIT)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAccount().getId()).isEqualTo(source.getId());
                    assertThat(entry.getAmount()).isEqualByComparingTo("40.00");
                });
        assertThat(entries)
                .filteredOn(entry -> entry.getDirection() == Direction.CREDIT)
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getAccount().getId()).isEqualTo(target.getId());
                    assertThat(entry.getAmount()).isEqualByComparingTo("40.00");
                });
    }

    @Test
    void create_rejects_same_account() {
        Account account = arrangeAccount("ada@pixflow.test", "100.00");
        PixKey ownKey = arrangePixKey(account, "11988887777");

        assertThatExceptionOfType(InvalidTransferException.class)
                .isThrownBy(() -> transfers.createTransfer(
                        account.getId(), ownKey.getKeyValue(), new BigDecimal("10.00"), "1"))
                .withMessage("Source and target accounts cannot be the same");
    }

    @Test
    void create_rejects_unknown_pix_key() {
        Account source = arrangeAccount("ada@pixflow.test", "100.00");

        assertThatExceptionOfType(PixKeyNotFoundException.class)
                .isThrownBy(() -> transfers.createTransfer(
                        source.getId(), "nobody@pixflow.test", new BigDecimal("10.00"), "1"));
    }

    @Test
    void create_rejects_unknown_source_account() {
        Account target = arrangeAccount("grace@pixflow.test", "20.00");
        PixKey pixKey = arrangePixKey(target, "11988887777");

        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> transfers.createTransfer(
                        UUID.randomUUID(), pixKey.getKeyValue(), new BigDecimal("10.00"), "1"));
    }

    @Test
    void create_rejects_insufficient_balance() {
        Account source = arrangeAccount("ada@pixflow.test", "100.00");
        Account target = arrangeAccount("grace@pixflow.test", "20.00");
        PixKey pixKey = arrangePixKey(target, "11988887777");

        assertThatExceptionOfType(InsufficientBalanceException.class)
                .isThrownBy(() -> transfers.createTransfer(
                        source.getId(), pixKey.getKeyValue(), new BigDecimal("100.01"), "1"));

        assertThat(accounts.findById(source.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100.00");
        assertThat(accounts.findById(target.getId()).orElseThrow().getBalance()).isEqualByComparingTo("20.00");
        assertThat(ledgerEntries.findByAccountId(source.getId())).isEmpty();
    }
}
