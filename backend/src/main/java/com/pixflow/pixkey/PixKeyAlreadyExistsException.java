package com.pixflow.pixkey;

public class PixKeyAlreadyExistsException extends RuntimeException {
  public PixKeyAlreadyExistsException(String message) {
    super(message);
  }
}
