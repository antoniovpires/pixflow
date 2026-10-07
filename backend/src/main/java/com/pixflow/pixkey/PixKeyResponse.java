package com.pixflow.pixkey;

import java.util.UUID;

public record PixKeyResponse(
    UUID id,
    UUID accountId,
    String keyValue,
    KeyType keyType) {

  public static PixKeyResponse from(PixKey pixKey) {
    return new PixKeyResponse(
        pixKey.getId(),
        pixKey.getAccount().getId(),
        pixKey.getKeyValue(),
        pixKey.getKeyType());
  }
}
