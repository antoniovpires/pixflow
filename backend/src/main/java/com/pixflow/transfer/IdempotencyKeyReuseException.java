package com.pixflow.transfer;

public class IdempotencyKeyReuseException extends RuntimeException {
  public IdempotencyKeyReuseException(String message) {
    super(message);
  }
}
