package com.pixflow.pixkey;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.transfer.Transfer;
import com.pixflow.user.User;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import java.math.BigDecimal;
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
@Import(PixKeyService.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PixKeyServiceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private PixKeyService pixKeys;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void create_stores_email_in_lowercase() {
        Account account = persistAccount("ada@pixflow.test");

        PixKey created = pixKeys.create(account.getId(), "Ada@Pixflow.test", KeyType.EMAIL);
        em.flush();
        em.clear();

        PixKey stored = pixKeyRepository.findById(created.getId()).orElseThrow();
        assertThat(stored.getKeyValue()).isEqualTo("ada@pixflow.test");
        assertThat(stored.getKeyType()).isEqualTo(KeyType.EMAIL);
        assertThat(stored.getAccount().getId()).isEqualTo(account.getId());
    }

    @Test
    void create_stores_phone_and_cpf_as_digits() {
        Account account = persistAccount("ada@pixflow.test");

        PixKey phone = pixKeys.create(account.getId(), "(11) 98888-7777", KeyType.PHONE);
        PixKey cpf = pixKeys.create(account.getId(), "123.456.789-00", KeyType.CPF);
        em.flush();
        em.clear();

        assertThat(pixKeyRepository.findById(phone.getId()).orElseThrow().getKeyValue()).isEqualTo("11988887777");
        assertThat(pixKeyRepository.findById(cpf.getId()).orElseThrow().getKeyValue()).isEqualTo("12345678900");
    }

    @Test
    void create_rejects_an_unknown_account() {
        assertThatExceptionOfType(AccountNotFoundException.class)
                .isThrownBy(() -> pixKeys.create(UUID.randomUUID(), "ada@pixflow.test", KeyType.EMAIL));
    }

    @Test
    void create_rejects_a_duplicate_normalized_key() {
        Account first = persistAccount("ada@pixflow.test");
        Account second = persistAccount("grace@pixflow.test");
        pixKeys.create(first.getId(), "Ada@Pixflow.test", KeyType.EMAIL);

        assertThatExceptionOfType(PixKeyAlreadyExistsException.class)
                .isThrownBy(() -> pixKeys.create(second.getId(), "ada@pixflow.test", KeyType.EMAIL));
    }

    @Test
    void delete_removes_a_pix_key() {
        Account account = persistAccount("ada@pixflow.test");
        PixKey created = pixKeys.create(account.getId(), "ada@pixflow.test", KeyType.EMAIL);

        pixKeys.delete(created.getId());

        assertThat(pixKeyRepository.findById(created.getId())).isEmpty();
    }

    @Test
    void delete_rejects_an_unknown_pix_key() {
        assertThatExceptionOfType(PixKeyNotFoundException.class)
                .isThrownBy(() -> pixKeys.delete(UUID.randomUUID()));
    }

    @Test
    void delete_rejects_a_pix_key_referenced_by_a_transfer() {
        Account source = persistAccount("ada@pixflow.test");
        Account target = persistAccount("grace@pixflow.test");
        PixKey created = pixKeys.create(target.getId(), "11988887777", KeyType.PHONE);
        em.persist(new Transfer(source, target, created, new BigDecimal("10.00"), "idem-1"));
        em.flush();
        em.clear();

        assertThatExceptionOfType(PixKeyInUseException.class)
                .isThrownBy(() -> pixKeys.delete(created.getId()));
    }

    private Account persistAccount(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        em.persist(user);

        Account account = new Account();
        account.setUser(user);
        em.persist(account);
        em.flush();
        return account;
    }
}
