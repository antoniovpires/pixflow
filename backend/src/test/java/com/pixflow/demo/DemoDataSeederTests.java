package com.pixflow.demo;

import com.pixflow.account.Account;
import com.pixflow.account.AccountRepository;
import com.pixflow.auth.AuthService;
import com.pixflow.auth.LoginRequest;
import com.pixflow.pixkey.PixKeyRepository;
import com.pixflow.user.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "pixflow.seed.enabled=true")
class DemoDataSeederTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private DemoDataSeeder seeder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE ledger_entries, transfers, pixkeys, accounts, users CASCADE");
    }

    @Test
    void seeds_a_user_with_three_keys_and_a_positive_balance() {
        seeder.run(new DefaultApplicationArguments());

        var user = userRepository.findByEmail(DemoDataSeeder.DEMO_EMAIL).orElseThrow();
        Account account = accountRepository.findAll().get(0);

        assertThat(account.getUser().getId()).isEqualTo(user.getId());
        assertThat(account.getBalance()).isEqualByComparingTo(DemoDataSeeder.DEMO_BALANCE);
        assertThat(pixKeyRepository.findByAccountId(account.getId())).hasSize(3);
    }

    @Test
    void demo_user_can_log_in() {
        seeder.run(new DefaultApplicationArguments());

        var token = authService.login(new LoginRequest(DemoDataSeeder.DEMO_EMAIL, DemoDataSeeder.DEMO_PASSWORD));

        assertThat(token.accessToken()).isNotBlank();
    }

    @Test
    void running_twice_does_not_duplicate_anything() {
        seeder.run(new DefaultApplicationArguments());
        seeder.run(new DefaultApplicationArguments());

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(accountRepository.count()).isEqualTo(1);
        assertThat(pixKeyRepository.count()).isEqualTo(3);
    }
}
