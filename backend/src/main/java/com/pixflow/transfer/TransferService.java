package com.pixflow.transfer;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.AccountRepository;
import com.pixflow.ledgerentry.LedgerEntry;
import com.pixflow.ledgerentry.LedgerEntryRepository;
import com.pixflow.pixkey.PixKeyNotFoundException;
import com.pixflow.pixkey.PixKeyRepository;
import com.pixflow.pixkey.PixKey;
import com.pixflow.ledgerentry.Direction;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.stereotype.Service;

@Service
public class TransferService {
  private final AccountRepository accountRepository;
  private final LedgerEntryRepository ledgerEntryRepository;
  private final TransferRepository transferRepository;
  private final PixKeyRepository pixKeyRepository;

  public TransferService(AccountRepository accountRepository, LedgerEntryRepository ledgerEntryRepository, TransferRepository transferRepository, PixKeyRepository pixKeyRepository) {
    this.accountRepository = accountRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
    this.transferRepository = transferRepository;
    this.pixKeyRepository = pixKeyRepository;
  }

  public List<Transfer> getTransfersForAccount(UUID accountId) {
    return transferRepository.findBySourceAccountIdOrTargetAccountIdOrderByCreatedAtDesc(accountId, accountId);
  }

  @Transactional(readOnly = true)
  public Optional<Transfer> findReplay(UUID sourceAccountId, String pixKeyValue, BigDecimal amount, String idempotencyKey) {
    return transferRepository.findBySourceAccountIdAndIdempotencyKey(sourceAccountId, idempotencyKey)
      .map(existing -> {
        boolean samePayload = existing.getPixKey().getKeyValue().equals(pixKeyValue)
            && existing.getAmount().compareTo(amount) == 0;
        if (!samePayload) {
          throw new IdempotencyKeyReuseException("Idempotency key was already used with a different request");
        }
        return existing;
      });
  }

  @Transactional
  public Transfer createTransfer(UUID sourceAccountId, String pixKeyValue, BigDecimal amount, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isEmpty()) {
      throw new IllegalArgumentException("Idempotency key cannot be null or empty");
    }

    Optional<Transfer> replay = findReplay(sourceAccountId, pixKeyValue, amount, idempotencyKey);
    if (replay.isPresent()) {
      return replay.get();
    }

    Account sourceAccount = accountRepository.findById(sourceAccountId)
      .orElseThrow(() -> new AccountNotFoundException("Source account not found"));
    PixKey pixKey = pixKeyRepository.findByKeyValue(pixKeyValue)
      .orElseThrow(() -> new PixKeyNotFoundException("Pix key not found"));
    Account targetAccount = pixKey.getAccount();

    if (sourceAccount.getId().equals(targetAccount.getId())) {
      throw new InvalidTransferException("Source and target accounts cannot be the same");
    }

    Transfer transfer = new Transfer(sourceAccount, targetAccount, pixKey, amount, idempotencyKey);

    sourceAccount.debit(amount);
    targetAccount.credit(amount);
    transferRepository.save(transfer);
    ledgerEntryRepository.save(new LedgerEntry(transfer, sourceAccount, amount, Direction.DEBIT));
    ledgerEntryRepository.save(new LedgerEntry(transfer, targetAccount, amount, Direction.CREDIT));

    transfer.markCompleted();
    return transfer;
  }
}
