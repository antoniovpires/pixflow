package com.pixflow.pixkey;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreatePixKeyRequest(
    @NotBlank String keyValue,
    @NotNull KeyType keyType) {}
