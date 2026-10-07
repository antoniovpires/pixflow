package com.pixflow.transfer;

import com.pixflow.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/transfers")
public class TransferController {
  private final TransferRetryService transferService;
  private final CurrentUser currentUser;

  public TransferController(TransferRetryService transferService, CurrentUser currentUser) {
    this.transferService = transferService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ResponseEntity<List<TransferResponse>> getAll(@AuthenticationPrincipal Jwt jwt) {
    UUID accountId = currentUser.accountId(jwt);
    List<Transfer> transfers = transferService.getTransfersForAccount(accountId);
    return ResponseEntity.ok(transfers.stream().map(TransferResponse::from).collect(Collectors.toList()));
  }

  @PostMapping
  public ResponseEntity<TransferResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateTransferRequest request) {
    UUID sourceAccountId = currentUser.accountId(jwt);
    Transfer transfer = transferService.createTransfer(
        sourceAccountId, request.pixKeyValue(), request.amount(), request.idempotencyKey());
    return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transfer));
  }
}
