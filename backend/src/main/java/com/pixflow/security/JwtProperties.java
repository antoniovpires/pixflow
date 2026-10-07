package com.pixflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;

@ConfigurationProperties(prefix = "pixflow.jwt")
public record JwtProperties(String secret, long expirationMinutes) {

  public JwtProperties {
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("pixflow.jwt.secret (JWT_SECRET) must be at least 32 bytes");
    }
    if (expirationMinutes <= 0) {
      throw new IllegalStateException("pixflow.jwt.expiration-minutes must be positive");
    }
  }
}
