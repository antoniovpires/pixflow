package com.pixflow.account;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.pixflow.ledgerentry.LedgerEntry;
import com.pixflow.ledgerentry.LedgerEntryRepository;
import com.pixflow.user.User;
import com.pixflow.user.UserNotFoundException;
import com.pixflow.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
  private final AccountRepository accountRepository;
  private final UserRepository userRepository;
  private final LedgerEntryRepository ledgerEntryRepository;

  public AccountService(
      AccountRepository accountRepository,
      UserRepository userRepository,
      LedgerEntryRepository ledgerEntryRepository) {
    this.accountRepository = accountRepository;
    this.userRepository = userRepository;
    this.ledgerEntryRepository = ledgerEntryRepository;
  }

  public List<Account> getAll() {
    return accountRepository.findAll();
  }

  public Account getById(UUID id) {
    return accountRepository.findById(id)
        .orElseThrow(() -> new AccountNotFoundException("Account not found"));
  }

  public List<LedgerEntry> getLedger(UUID accountId) {
    getById(accountId);
    return ledgerEntryRepository.findByAccountIdOrderByCreatedAtAsc(accountId);
  }

  @Transactional
  public Account create(UUID userId) {
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found"));
    Account account = new Account();
    account.setUser(user);
    return accountRepository.save(account);
  }

  @Transactional
  public Account update(UUID id, UUID userId) {
    Account account = getById(id);
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException("User not found"));
    account.setUser(user);
    return accountRepository.save(account);
  }

  @Transactional
  public void delete(UUID id) {
    Account account = getById(id);
    if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
      throw new InvalidAccountException("Account balance must be zero");
    }
    try {
      accountRepository.delete(account);
      accountRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new AccountInUseException("Account is still referenced");
    }
  }
}
