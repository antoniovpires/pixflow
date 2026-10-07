package com.pixflow.demo;

import com.pixflow.account.Account;
import com.pixflow.account.AccountRepository;
import com.pixflow.auth.AuthService;
import com.pixflow.auth.RegisterRequest;
import com.pixflow.auth.RegisterResponse;
import com.pixflow.pixkey.KeyType;
import com.pixflow.pixkey.PixKeyService;
import com.pixflow.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Creates a demo user once, so a fresh install has something to log in with.
 * Off by default; enable with pixflow.seed.enabled=true (env PIXFLOW_SEED_ENABLED=true).
 * Safe to run on every startup: it does nothing if the demo user already exists.
 */
@Component
@ConditionalOnProperty(name = "pixflow.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {
  static final String DEMO_EMAIL = "ada@pixflow.demo";
  static final String DEMO_PASSWORD = "demo-password-123";
  static final BigDecimal DEMO_BALANCE = new BigDecimal("5000.00");

  private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

  private final UserRepository userRepository;
  private final AccountRepository accountRepository;
  private final AuthService authService;
  private final PixKeyService pixKeyService;

  public DemoDataSeeder(
      UserRepository userRepository,
      AccountRepository accountRepository,
      AuthService authService,
      PixKeyService pixKeyService) {
    this.userRepository = userRepository;
    this.accountRepository = accountRepository;
    this.authService = authService;
    this.pixKeyService = pixKeyService;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (userRepository.findByEmail(DEMO_EMAIL).isPresent()) {
      log.info("Demo data already present, skipping seed");
      return;
    }

    RegisterResponse registered =
        authService.register(new RegisterRequest("Ada Lovelace", DEMO_EMAIL, DEMO_PASSWORD));

    pixKeyService.create(registered.accountId(), DEMO_EMAIL, KeyType.EMAIL);
    pixKeyService.create(registered.accountId(), "+55 11 99999-0001", KeyType.PHONE);
    pixKeyService.create(registered.accountId(), "123.456.789-09", KeyType.CPF);

    // Demo shortcut: the starting balance is credited directly and has no ledger entry,
    // because the app has no "deposit" operation yet (see docs/known-gaps.md, T7).
    Account account = accountRepository.findById(registered.accountId()).orElseThrow();
    account.credit(DEMO_BALANCE);
    accountRepository.save(account);

    log.info("Seeded demo user {} (password: {})", DEMO_EMAIL, DEMO_PASSWORD);
  }
}
