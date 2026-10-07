package com.pixflow.security;

import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.AccountRepository;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class CurrentUser {
  private final AccountRepository accountRepository;

  public CurrentUser(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  public UUID userId(Jwt jwt) {
    return UUID.fromString(jwt.getSubject());
  }

  public UUID accountId(Jwt jwt) {
    return accountRepository.findByUserId(userId(jwt))
        .orElseThrow(() -> new AccountNotFoundException("Account not found"))
        .getId();
  }
}
