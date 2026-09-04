package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.AuthResponse;
import com.wavetransakt.security.JwtService;
import com.wavetransakt.user.dto.LoginRequest;
import com.wavetransakt.user.dto.RegisterRequest;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.service.VerificationService;
import com.wavetransakt.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WalletService walletService;
    private final VerificationService verificationService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {

        // Check email
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException(
                    "Email already registered"
            );
        }

        // Check phone
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException(
                    "Phone number already registered"
            );
        }

        // Create user
        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(
                        passwordEncoder.encode(
                                request.getPassword()
                        )
                )
                .bvn(request.getBvn())
                .nin(request.getNin())
                .build();

        // Save user first
        user = userRepository.save(user);

        // Create wallet
        walletService.createWallet(user);

        // IMPORTANT:
        // Create and SAVE verification code
        String verificationCode =
                verificationService.createEmailVerificationCode(user);

        /*
         * DEMO MODE:
         * We return the code in the response so you can test
         * the Android application before connecting an email/SMS
         * provider.
         *
         * In production, DO NOT return the verification code.
         */

        return AuthResponse.builder()
                .message(
                        "Registration successful. " +
                                "Please verify your email."
                )
                .verificationCode(verificationCode)
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getIdentifier())
                .orElseGet(() ->
                        userRepository
                                .findByPhone(request.getIdentifier())
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Invalid email/phone or password"
                                        )
                                )
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Invalid email/phone or password"
            );
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException(
                    "Account is not active. Please verify your email."
            );
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail()
        );

        return AuthResponse.builder()
                .message("Login successful")
                .token(token)
                .build();
    }
}