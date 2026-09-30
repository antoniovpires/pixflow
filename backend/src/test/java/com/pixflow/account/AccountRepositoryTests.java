package com.pixflow.account;

import com.pixflow.account.Account;
import com.pixflow.account.AccountRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AccountRepositoryTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");
    
    @Autowired
    private AccountRepository accounts;

    @Autowired
    private TestEntityManager em;

    private Account arrangeAccount() {
        User user = new User();
        user.setEmail("ada@pixflow.test");
        user.setPasswordHash("hash");
        em.persist(user);

        Account account = new Account();
        account.setUser(user);
        account.credit(new BigDecimal("100"));
        em.persist(account);
        em.flush();
        em.clear();
        
        return accounts.findById(account.getId()).orElseThrow();
    }

    @Test
    void debit() {
        Account account = arrangeAccount();
        Long version = account.getVersion();

        account.debit(new BigDecimal("10"));
        em.flush();
        em.clear();

        Account stored = accounts.findById(account.getId()).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("90");
        assertThat(stored.getVersion()).isEqualTo(version + 1);
    }

    @Test
    void credit() {
        Account account = arrangeAccount();
        Long version = account.getVersion();

        account.credit(new BigDecimal("10"));
        em.flush();
        em.clear();
        
        Account stored = accounts.findById(account.getId()).orElseThrow();
        assertThat(stored.getBalance()).isEqualByComparingTo("110");
        assertThat(stored.getVersion()).isEqualTo(version + 1);
    }
}