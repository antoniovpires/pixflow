package com.pixflow.auth;

public class EmailAlreadyUsedException extends RuntimeException {
  public EmailAlreadyUsedException(String message) {
    super(message);
  }
}
