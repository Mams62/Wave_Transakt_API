package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.AuthResponse;
import com.wavetransakt.auth.dto.LoginOtpRequest;
import com.wavetransakt.security.JwtService;
import com.wavetransakt.user.dto.LoginRequest;
import com.wavetransakt.user.dto.RegisterRequest;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.service.SmsOtpSender;
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
    private final SmsOtpSender smsOtpSender;

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
                .password(passwordEncoder.encode(request.getAccountPin()))
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
                .message("Registration successful. Please verify your email.")
                .verificationCode(returnVerificationCode ? verificationCode : null)
                .requiresOtp(false)
                .faceVerificationRequired(false)
                .build();
    }

    /**
     * Credential login is now a challenge-start operation. A JWT is not returned
     * until the OTP sent to the registered phone number has been verified.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = requireUserByIdentifier(request.getIdentifier());

        if (!passwordEncoder.matches(
                request.getAccountPin(),
                user.getPassword()
        )) {
            throw new IllegalArgumentException(
                    "Invalid email/phone or account PIN"
            );
        }

        if (!user.isEnabled()) {
            throw new IllegalArgumentException(
                    "Account is not active. Please verify your email."
            );
        }

        String otp = verificationService.createPhoneVerificationCode(user);
        boolean delivered = smsOtpSender.sendLoginOtp(user.getPhone(), otp);

        if (!delivered && !returnVerificationCode) {
            throw new IllegalStateException(
                    "Phone verification is temporarily unavailable. Please try again later."
            );
        }

        return AuthResponse.builder()
                .message(
                        delivered
                                ? "A 6-digit login code was sent to your registered phone number."
                                : "Controlled-stage login code generated. Configure the SMS provider before production."
                )
                .verificationCode(returnVerificationCode ? otp : null)
                .requiresOtp(true)
                .maskedPhone(maskPhone(user.getPhone()))
                .faceVerificationRequired(false)
                .build();
    }

    @Transactional
    public AuthResponse verifyLoginOtp(LoginOtpRequest request) {
        User user = verificationService.verifyLoginPhoneCode(
                request.getIdentifier(),
                request.getOtp()
        );

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
                .message("Login verified successfully")
                .token(token)
                .requiresOtp(false)
                // Cross-device face matching must remain false until a real
                // liveness/identity provider performs server-side verification.
                .faceVerificationRequired(false)
                .build();
    }

    private User requireUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Email or phone is required");
        }

        String raw = identifier.trim();
        return userRepository
                .findByEmail(raw.toLowerCase(Locale.ROOT))
                .orElseGet(() ->
                        userRepository
                                .findByPhone(raw)
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "Invalid email/phone or account PIN"
                                        )
                                )
                );
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return "registered phone";
        }

        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) {
            return "****";
        }
        return "***" + digits.substring(digits.length() - 4);
    }
}
