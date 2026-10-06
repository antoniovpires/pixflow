package com.pixflow.transfer;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transfers")
public class TransferController {
  private final TransferRetryService transferService;

  public TransferController(TransferRetryService transferService) {
    this.transferService = transferService;
  }

  @PostMapping
  public ResponseEntity<TransferResponse> create(@Valid @RequestBody CreateTransferRequest request) {
    Transfer transfer = transferService.createTransfer(
        request.sourceAccountId(), request.pixKeyValue(), request.amount(), request.idempotencyKey());
    return ResponseEntity.status(HttpStatus.CREATED).body(TransferResponse.from(transfer));
  }
}
