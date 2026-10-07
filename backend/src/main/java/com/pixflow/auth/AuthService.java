package com.pixflow.auth;

import com.pixflow.account.Account;
import com.pixflow.account.AccountService;
import com.pixflow.user.User;
import com.pixflow.user.UserRepository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {
  private final UserRepository userRepository;
  private final AccountService accountService;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final String dummyHash;

  public AuthService(
      UserRepository userRepository,
      AccountService accountService,
      PasswordEncoder passwordEncoder,
      JwtService jwtService) {
    this.userRepository = userRepository;
    this.accountService = accountService;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.dummyHash = passwordEncoder.encode("not-a-real-password");
  }

  @Transactional
  public RegisterResponse register(RegisterRequest request) {
    String email = normalize(request.email());
    if (userRepository.findByEmail(email).isPresent()) {
      throw new EmailAlreadyUsedException("Email already registered");
    }
    User user = new User();
    user.setName(request.name());
    user.setEmail(email);
    user.setPasswordHash(passwordEncoder.encode(request.password()));
    try {
      userRepository.saveAndFlush(user);
    } catch (DataIntegrityViolationException e) {
      throw new EmailAlreadyUsedException("Email already registered");
    }
    Account account = accountService.create(user.getId());
    return new RegisterResponse(user.getId(), account.getId());
  }

  public TokenResponse login(LoginRequest request) {
    User user = userRepository.findByEmail(normalize(request.email())).orElse(null);
    String hash = user != null ? user.getPasswordHash() : dummyHash;
    boolean passwordMatches = passwordEncoder.matches(request.password(), hash);
    if (user == null || !passwordMatches) {
      throw new InvalidCredentialsException("Invalid email or password");
    }
    return jwtService.issue(user.getId());
  }

  private String normalize(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }
}
