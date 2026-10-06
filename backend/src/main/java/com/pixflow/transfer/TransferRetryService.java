package com.pixflow.transfer;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

// Deliberately NOT @Transactional: each call to transferService goes through its
// transactional proxy, so every attempt gets a fresh transaction and a fresh read.
@Service
public class TransferRetryService {
  private static final int MAX_ATTEMPTS = 5;

  private final TransferService transferService;

  public TransferRetryService(TransferService transferService) {
    this.transferService = transferService;
  }

  public Transfer createTransfer(UUID sourceAccountId, String pixKeyValue, BigDecimal amount, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isEmpty()) {
      throw new IllegalArgumentException("Idempotency key cannot be null or empty");
    }

    for (int attempt = 1; ; attempt++) {
      try {
        return transferService.createTransfer(sourceAccountId, pixKeyValue, amount, idempotencyKey);
      } catch (OptimisticLockingFailureException e) {
        if (attempt == MAX_ATTEMPTS) {
          throw new TransferConflictException(
              "Transfer failed after " + MAX_ATTEMPTS + " attempts due to concurrent updates", e);
        }
      }
    }
  }
}
