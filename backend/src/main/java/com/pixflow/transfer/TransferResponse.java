package com.pixflow.transfer;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
    UUID id,
    Status status,
    BigDecimal amount,
    UUID sourceAccountId,
    UUID targetAccountId) {

  public static TransferResponse from(Transfer transfer) {
    return new TransferResponse(
        transfer.getId(),
        transfer.getStatus(),
        transfer.getAmount(),
        transfer.getSourceAccount().getId(),
        transfer.getTargetAccount().getId());
  }
}
