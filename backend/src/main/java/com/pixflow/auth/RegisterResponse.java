package com.pixflow.auth;

import java.util.UUID;

public record RegisterResponse(UUID userId, UUID accountId) {}
