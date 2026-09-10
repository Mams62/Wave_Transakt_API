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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final WalletService walletService;
    private final VerificationService verificationService;

    @Value("${wave.demo.return-verification-code:false}")
    private boolean returnVerificationCode;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail()
                .trim()
                .toLowerCase(Locale.ROOT);
        String phone = request.getPhone().trim();
        String bvn = request.getBvn().trim();
        String nin = request.getNin().trim();

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        if (userRepository.existsByPhone(phone)) {
            throw new IllegalArgumentException("Phone number already registered");
        }

        if (userRepository.existsByBvn(bvn)) {
            throw new IllegalArgumentException("BVN already linked to an account");
        }

        if (userRepository.existsByNin(nin)) {
            throw new IllegalArgumentException("NIN already linked to an account");
        }

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getPassword()))
                .bvn(bvn)
                .nin(nin)
                .state(request.getState().trim())
                .localGovernment(request.getLocalGovernment().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender().trim())
                .transactionPinHash(
                        passwordEncoder.encode(request.getTransactionPin())
                )
                .build();

        user = userRepository.save(user);

        walletService.createWallet(user);

        String verificationCode =
                verificationService.createEmailVerificationCode(user);

        return AuthResponse.builder()
                .message(
                        "Registration successful. Please verify your email."
                )
                .verificationCode(
                        returnVerificationCode ? verificationCode : null
                )
                .build();
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String identifier = request.getIdentifier().trim();
        String emailIdentifier = identifier.toLowerCase(Locale.ROOT);

        User user = userRepository
                .findByEmail(emailIdentifier)
                .orElseGet(() ->
                        userRepository
                                .findByPhone(identifier)
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
