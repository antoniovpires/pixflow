package com.pixflow.transfer;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

// The JSON body of POST /transfers. A record is an immutable data holder:
// Jackson fills it from the JSON, Bean Validation checks the annotations.
public record CreateTransferRequest(
    @NotNull UUID sourceAccountId, // TODO: take from the authenticated user once JWT exists
    @NotBlank String pixKeyValue,
    @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
    @NotBlank String idempotencyKey) {}
