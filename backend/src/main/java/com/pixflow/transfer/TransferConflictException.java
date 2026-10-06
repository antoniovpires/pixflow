package com.pixflow.transfer;

// Thrown when optimistic-lock retries are exhausted (ADR-0002).
public class TransferConflictException extends RuntimeException {
  public TransferConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
