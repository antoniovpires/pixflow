package com.pixflow.auth;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {}
