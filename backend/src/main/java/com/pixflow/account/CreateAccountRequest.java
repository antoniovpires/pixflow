package com.pixflow.account;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateAccountRequest(@NotNull UUID userId) {}
