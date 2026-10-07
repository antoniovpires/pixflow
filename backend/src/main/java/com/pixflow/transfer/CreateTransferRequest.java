package com.pixflow.transfer;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateTransferRequest(
    @NotBlank String pixKeyValue,
    @NotNull @Positive @Digits(integer = 8, fraction = 2) BigDecimal amount,
    @NotBlank String idempotencyKey) {}
