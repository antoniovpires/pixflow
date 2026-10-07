package com.pixflow.account;

import com.pixflow.ledgerentry.LedgerEntryResponse;
import com.pixflow.security.CurrentUser;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/accounts")
public class AccountController {
  private final AccountService accountService;
  private final CurrentUser currentUser;

  public AccountController(AccountService accountService, CurrentUser currentUser) {
    this.accountService = accountService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ResponseEntity<List<AccountResponse>> getAll(@AuthenticationPrincipal Jwt jwt) {
    Account account = accountService.getById(currentUser.accountId(jwt));
    return ResponseEntity.ok(List.of(AccountResponse.from(account)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<AccountResponse> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    requireOwn(jwt, id);
    return ResponseEntity.ok(AccountResponse.from(accountService.getById(id)));
  }

  @GetMapping("/{id}/ledger")
  public ResponseEntity<List<LedgerEntryResponse>> getLedger(
      @AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    requireOwn(jwt, id);
    return ResponseEntity.ok(
        accountService.getLedger(id).stream().map(LedgerEntryResponse::from).collect(Collectors.toList()));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    requireOwn(jwt, id);
    accountService.delete(id);
    return ResponseEntity.noContent().build();
  }

  private void requireOwn(Jwt jwt, UUID accountId) {
    if (!currentUser.accountId(jwt).equals(accountId)) {
      throw new AccountNotFoundException("Account not found");
    }
  }
}
