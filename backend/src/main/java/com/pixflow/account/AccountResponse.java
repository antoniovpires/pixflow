package com.pixflow.account;

import java.math.BigDecimal;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    UUID userId,
    BigDecimal balance,
    String currency) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getUser().getId(),
        account.getBalance(),
        account.getCurrency());
  }
}
