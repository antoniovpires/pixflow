package com.pixflow.account;

public class AccountInUseException extends RuntimeException {
  public AccountInUseException(String message) {
    super(message);
  }
}
