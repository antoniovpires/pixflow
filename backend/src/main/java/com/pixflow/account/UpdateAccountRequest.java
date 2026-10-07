package com.pixflow.account;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateAccountRequest(@NotNull UUID userId) {}
