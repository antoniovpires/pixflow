package com.pixflow.transfer;

import java.math.BigDecimal;
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

  @Transactional
  public Transfer createTransfer(UUID sourceAccountId, String pixKeyValue, BigDecimal amount, String idempotencyKey) {
    if (idempotencyKey == null || idempotencyKey.isEmpty()) {
      throw new IllegalArgumentException("Idempotency key cannot be null or empty");
    }

    Optional<Transfer> existingTransfer = transferRepository.findByIdempotencyKey(idempotencyKey);
    if (existingTransfer.isPresent()) {
      return existingTransfer.get();
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
