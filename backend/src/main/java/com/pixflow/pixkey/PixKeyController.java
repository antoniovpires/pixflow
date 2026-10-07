package com.pixflow.pixkey;

import com.pixflow.security.CurrentUser;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/pixkeys")
public class PixKeyController {
  private final PixKeyService pixKeyService;
  private final CurrentUser currentUser;

  public PixKeyController(PixKeyService pixKeyService, CurrentUser currentUser) {
    this.pixKeyService = pixKeyService;
    this.currentUser = currentUser;
  }

  @GetMapping
  public ResponseEntity<List<PixKeyResponse>> getAll(@AuthenticationPrincipal Jwt jwt) {
    List<PixKey> pixKeys = pixKeyService.getAllForAccount(currentUser.accountId(jwt));
    return ResponseEntity.ok(pixKeys.stream().map(PixKeyResponse::from).collect(Collectors.toList()));
  }

  @GetMapping("/{id}")
  public ResponseEntity<PixKeyResponse> getById(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    return ResponseEntity.ok(PixKeyResponse.from(pixKeyService.getOwned(id, currentUser.accountId(jwt))));
  }

  @PostMapping
  public ResponseEntity<PixKeyResponse> create(
      @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreatePixKeyRequest request) {
    PixKey pixKey = pixKeyService.create(currentUser.accountId(jwt), request.keyValue(), request.keyType());
    return ResponseEntity.status(HttpStatus.CREATED).body(PixKeyResponse.from(pixKey));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
    pixKeyService.deleteOwned(id, currentUser.accountId(jwt));
    return ResponseEntity.noContent().build();
  }
}
