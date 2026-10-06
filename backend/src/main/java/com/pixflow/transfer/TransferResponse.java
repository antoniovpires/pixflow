package com.pixflow.transfer;

import java.math.BigDecimal;
import java.util.UUID;

// What the API returns. Deliberately NOT the Transfer entity: the entity has lazy
// relations and is a persistence detail, not a public contract.
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
