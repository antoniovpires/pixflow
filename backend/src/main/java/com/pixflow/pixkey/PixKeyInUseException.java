package com.pixflow.pixkey;

public class PixKeyInUseException extends RuntimeException {
  public PixKeyInUseException(String message) {
    super(message);
  }
}
