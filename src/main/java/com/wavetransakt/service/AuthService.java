package com.wavetransakt.service;

import com.wavetransakt.dto.AuthRequests.LoginRequest;
import com.wavetransakt.dto.AuthRequests.RegisterRequest;
import com.wavetransakt.model.User;
import com.wavetransakt.model.Wallet;
import com.wavetransakt.repository.UserRepository;
import com.wavetransakt.repository.WalletRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class AuthService {
    private final UserRepository users;
    private final WalletRepository wallets;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;

    public AuthService(UserRepository users, WalletRepository wallets, PasswordEncoder passwordEncoder, JwtEncoder jwtEncoder) {
        this.users = users;
        this.wallets = wallets;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setWalletNumber(generateWalletNumber());
        user = users.save(user);

        Wallet wallet = new Wallet();
        wallet.setUser(user);
        wallets.save(wallet);

        return new AuthResponse("Registration successful", createToken(user));
    }

    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmailIgnoreCase(request.email().trim().toLowerCase())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return new AuthResponse("Login successful", createToken(user));
    }

    public User findAuthenticatedUser(UUID id) {
        return users.findById(id).orElseThrow(() -> new BadCredentialsException("Authenticated user no longer exists"));
    }

    private String createToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("wave-transakt-api")
                .subject(user.getId().toString())
                .claim("email", user.getEmail())
                .issuedAt(now)
                .expiresAt(now.plus(30, ChronoUnit.MINUTES))
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateWalletNumber() {
        String number;
        do {
            number = "WT" + String.format("%010d", Math.abs(java.util.concurrent.ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L)));
        } while (users.existsByWalletNumber(number));
        return number;
    }

    public record AuthResponse(String message, String token) {}
}
