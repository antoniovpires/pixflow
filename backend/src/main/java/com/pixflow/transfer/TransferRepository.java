package com.pixflow.transfer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, UUID> {
  List<Transfer> findByPixKeyId(UUID pixKeyId);
  List<Transfer> findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(UUID sourceAccountId, UUID targetAccountId);
  Optional<Transfer> findBySourceAccountIdAndIdempotencyKey(UUID sourceAccountId, String idempotencyKey);
}