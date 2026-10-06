package com.pixflow.transfer;

// Extends IllegalArgumentException so existing callers/tests that expect it keep working.
public class InvalidTransferException extends IllegalArgumentException {
  public InvalidTransferException(String message) {
    super(message);
  }
}
