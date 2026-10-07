package com.pixflow.auth;

import com.pixflow.security.JwtProperties;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class JwtService {
  private final JwtEncoder jwtEncoder;
  private final Duration lifetime;

  public JwtService(JwtEncoder jwtEncoder, JwtProperties properties) {
    this.jwtEncoder = jwtEncoder;
    this.lifetime = Duration.ofMinutes(properties.expirationMinutes());
  }

  public TokenResponse issue(UUID userId) {
    Instant now = Instant.now();
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .subject(userId.toString())
        .issuedAt(now)
        .expiresAt(now.plus(lifetime))
        .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new TokenResponse(token, "Bearer", lifetime.toSeconds());
  }
}
