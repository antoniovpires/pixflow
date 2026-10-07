package com.pixflow.transfer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.pixflow.account.Account;
import com.pixflow.account.AccountRepository;
import com.pixflow.pixkey.KeyType;
import com.pixflow.pixkey.PixKey;
import com.pixflow.pixkey.PixKeyRepository;
import com.pixflow.user.User;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest
public class TransferConcurrencyTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17.1");

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PixKeyRepository pixKeyRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransferRetryService transferRetryService;

    private Account ada;
    private Account bruno;
    private PixKey brunoPixKey;

    private void arrange(BigDecimal adaStartingBalance) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            User adaUser = new User();
            adaUser.setName("Ada");
            adaUser.setEmail("ada@pixflow.test");
            adaUser.setPasswordHash("hash");
            entityManager.persist(adaUser);

            Account adaAccount = new Account();
            adaAccount.setUser(adaUser);
            adaAccount.credit(adaStartingBalance);
            ada = accountRepository.save(adaAccount);

            User brunoUser = new User();
            brunoUser.setName("Bruno");
            brunoUser.setEmail("bruno@pixflow.test");
            brunoUser.setPasswordHash("hash");
            entityManager.persist(brunoUser);

            Account brunoAccount = new Account();
            brunoAccount.setUser(brunoUser);
            bruno = accountRepository.save(brunoAccount);

            brunoPixKey = pixKeyRepository.save(
                    new PixKey(brunoAccount, "bruno@pixflow.test", KeyType.EMAIL));
        });
    }

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE ledger_entries, transfers, pixkeys, accounts, users CASCADE");
    }

    @RepeatedTest(20)
    public void testTransferConcurrency() throws Exception {
        arrange(new BigDecimal("100"));
        
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<Transfer> first = pool.submit(() -> {
          barrier.await();
          return transferService.createTransfer(
            ada.getId(),
            brunoPixKey.getKeyValue(),
            new BigDecimal("60.00"),
            "1"
          );
        });

        Future<Transfer> second = pool.submit(() -> {
          barrier.await();
          return transferService.createTransfer(
            ada.getId(),
            brunoPixKey.getKeyValue(),
            new BigDecimal("50.00"),
            "2"
          );
        });

        int successes = 0;
        int failures = 0;
        for (Future<Transfer> future : List.of(first, second)) {
            try {
                future.get();
                successes++;
            } catch (ExecutionException e) {
                failures++;
            }
        }
        pool.shutdown();

        assertEquals(1, successes);
        assertEquals(1, failures);

        BigDecimal adaBalance = accountRepository.findById(ada.getId()).orElseThrow().getBalance();
        BigDecimal brunoBalance = accountRepository.findById(bruno.getId()).orElseThrow().getBalance();

        assertEquals(0, new BigDecimal("100").compareTo(adaBalance.add(brunoBalance)));

        assertTrue(adaBalance.compareTo(new BigDecimal("40")) == 0
                || adaBalance.compareTo(new BigDecimal("50")) == 0);

        assertEquals(1, count("SELECT count(*) FROM transfers WHERE status = 'SUCCESS'"));
        assertEquals(1, count("SELECT count(*) FROM transfers"));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries"));
    }

    @RepeatedTest(20)
    public void testTransferConcurrencyWithRetry() throws Exception {
        arrange(new BigDecimal("200"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        Future<Transfer> first = pool.submit(() -> {
            barrier.await();
            return transferRetryService.createTransfer(
                    ada.getId(), brunoPixKey.getKeyValue(), new BigDecimal("60.00"), "1");
        });

        Future<Transfer> second = pool.submit(() -> {
            barrier.await();
            return transferRetryService.createTransfer(
                    ada.getId(), brunoPixKey.getKeyValue(), new BigDecimal("50.00"), "2");
        });

        first.get(10, TimeUnit.SECONDS);
        second.get(10, TimeUnit.SECONDS);
        pool.shutdown();

        BigDecimal adaBalance = accountRepository.findById(ada.getId()).orElseThrow().getBalance();
        BigDecimal brunoBalance = accountRepository.findById(bruno.getId()).orElseThrow().getBalance();

        assertEquals(0, new BigDecimal("90").compareTo(adaBalance));
        assertEquals(0, new BigDecimal("110").compareTo(brunoBalance));

        assertEquals(2, count("SELECT count(*) FROM transfers WHERE status = 'SUCCESS'"));
        assertEquals(4, count("SELECT count(*) FROM ledger_entries"));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE direction = 'DEBIT'"));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries WHERE direction = 'CREDIT'"));
    }

    @RepeatedTest(20)
    public void testSameIdempotencyKeyConcurrentlyMovesMoneyOnce() throws Exception {
        arrange(new BigDecimal("100"));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);

        List<Future<Transfer>> futures = List.of(
                pool.submit(() -> {
                    barrier.await();
                    return transferRetryService.createTransfer(
                            ada.getId(), brunoPixKey.getKeyValue(), new BigDecimal("60.00"), "same-key");
                }),
                pool.submit(() -> {
                    barrier.await();
                    return transferRetryService.createTransfer(
                            ada.getId(), brunoPixKey.getKeyValue(), new BigDecimal("60.00"), "same-key");
                }));

        Transfer a = futures.get(0).get(10, TimeUnit.SECONDS);
        Transfer b = futures.get(1).get(10, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(a.getId(), b.getId());

        BigDecimal adaBalance = accountRepository.findById(ada.getId()).orElseThrow().getBalance();
        BigDecimal brunoBalance = accountRepository.findById(bruno.getId()).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("40").compareTo(adaBalance));
        assertEquals(0, new BigDecimal("60").compareTo(brunoBalance));
        assertEquals(1, count("SELECT count(*) FROM transfers"));
        assertEquals(2, count("SELECT count(*) FROM ledger_entries"));
    }

    private int count(String sql) {
        return jdbcTemplate.queryForObject(sql, Integer.class);
    }
}