package com.pixflow.ledgerentry;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record LedgerEntryResponse(
    UUID id,
    UUID transferId,
    BigDecimal amount,
    Direction direction,
    LocalDateTime createdAt) {

  public static LedgerEntryResponse from(LedgerEntry entry) {
    return new LedgerEntryResponse(
        entry.getId(),
        entry.getTransfer().getId(),
        entry.getAmount(),
        entry.getDirection(),
        entry.getCreatedAt());
  }
}
