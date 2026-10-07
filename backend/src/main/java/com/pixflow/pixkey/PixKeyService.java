package com.pixflow.pixkey;

import java.util.List;
import java.util.UUID;

import com.pixflow.account.Account;
import com.pixflow.account.AccountNotFoundException;
import com.pixflow.account.AccountRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PixKeyService {
  private final PixKeyRepository pixKeyRepository;
  private final AccountRepository accountRepository;

  public PixKeyService(PixKeyRepository pixKeyRepository, AccountRepository accountRepository) {
    this.pixKeyRepository = pixKeyRepository;
    this.accountRepository = accountRepository;
  }

  public List<PixKey> getAll() {
    return pixKeyRepository.findAll();
  }

  public List<PixKey> getAllForAccount(UUID accountId) {
    return pixKeyRepository.findByAccountId(accountId);
  }

  public PixKey getOwned(UUID id, UUID accountId) {
    PixKey pixKey = getById(id);
    if (!pixKey.getAccount().getId().equals(accountId)) {
      throw new PixKeyNotFoundException("Pix key not found");
    }
    return pixKey;
  }

  @Transactional
  public void deleteOwned(UUID id, UUID accountId) {
    getOwned(id, accountId);
    delete(id);
  }

  public PixKey getById(UUID id) {
    return pixKeyRepository.findById(id)
        .orElseThrow(() -> new PixKeyNotFoundException("Pix key not found"));
  }

  @Transactional
  public PixKey create(UUID accountId, String keyValue, KeyType keyType) {
    Account account = accountRepository.findById(accountId)
        .orElseThrow(() -> new AccountNotFoundException("Account not found"));
    PixKey pixKey = new PixKey(account, keyValue, keyType);
    try {
      return pixKeyRepository.saveAndFlush(pixKey);
    } catch (DataIntegrityViolationException e) {
      throw new PixKeyAlreadyExistsException("Pix key already exists");
    }
  }

  @Transactional
  public void delete(UUID id) {
    PixKey pixKey = getById(id);
    try {
      pixKeyRepository.delete(pixKey);
      pixKeyRepository.flush();
    } catch (DataIntegrityViolationException e) {
      throw new PixKeyInUseException("Pix key is still referenced");
    }
  }
}
