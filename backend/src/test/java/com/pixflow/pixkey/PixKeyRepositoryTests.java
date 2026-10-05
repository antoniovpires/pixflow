package com.pixflow.pixkey;

import com.pixflow.account.Account;
import com.pixflow.account.AccountRepository;
import com.pixflow.pixkey.PixKey;
import com.pixflow.pixkey.PixKeyRepository;
import com.pixflow.pixkey.KeyType;
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
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PixKeyRepositoryTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private PixKeyRepository pixKeys;

    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TestEntityManager em;

    private Account arrangeAccount(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("hash");
        em.persist(user);

        Account account = new Account();
        account.setUser(user);
        em.persist(account);
        em.flush();
        em.clear();
        
        return accounts.findById(account.getId()).orElseThrow();
    }

      @Test
      void create() {
          Account account = arrangeAccount("ada@pixflow.test");
          PixKey pixKey = new PixKey(account, "1234567890", KeyType.PHONE);
          pixKeys.save(pixKey);
          em.flush();
          em.clear();

          PixKey stored = pixKeys.findById(pixKey.getId()).orElseThrow();
          assertThat(stored.getAccount().getId()).isEqualTo(account.getId());
          assertThat(stored.getKeyValue()).isEqualTo("1234567890");
      }

      @Test
      void create_with_duplicate_key() {
        Account account = arrangeAccount("ada@pixflow.test");
        PixKey pixKey = new PixKey(account, "1234567890", KeyType.PHONE);
        pixKeys.save(pixKey);
        em.flush();
        em.clear();

        Account newAccount = arrangeAccount("john.doe@pixflow.test");
        PixKey pixKey2 = new PixKey(newAccount, "1234567890", KeyType.PHONE);
        assertThatExceptionOfType(DataIntegrityViolationException.class)
        .isThrownBy(() -> pixKeys.saveAndFlush(pixKey2));
      }

      @Test
      void find_by_key_value() {
        Account account = arrangeAccount("ada@pixflow.test");
        PixKey pixKey = new PixKey(account, "1234567890", KeyType.PHONE);
        pixKeys.save(pixKey);
        em.flush();
        em.clear();
        assertThat(pixKeys.findByKeyValue("1234567890")).isPresent();
        assertThat(pixKeys.findByKeyValue("1234567890").get().getAccount().getId()).isEqualTo(account.getId());
        assertThat(pixKeys.findByKeyValue("1234567890").get().getKeyValue()).isEqualTo("1234567890");
      }

      @Test
      void find_by_key_value_not_found() {
        assertThat(pixKeys.findByKeyValue("1234567890")).isEmpty();
      }
}