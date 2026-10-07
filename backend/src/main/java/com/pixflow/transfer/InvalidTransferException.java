package com.pixflow.transfer;

public class InvalidTransferException extends IllegalArgumentException {
  public InvalidTransferException(String message) {
    super(message);
  }
}
