package com.wavetransakt.verification.service;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.verification.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public String createEmailVerificationCode(User user) {
        return createCode(user, VerificationType.EMAIL);
    }

    @Transactional
    public String createPhoneVerificationCode(User user) {
        return createCode(user, VerificationType.PHONE);
    }

    @Transactional
    public String resendEmailVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        User user = userRepository
                .findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalArgumentException("Email is already verified");
        }

        return createEmailVerificationCode(user);
    }

    @Transactional
    public void verifyEmail(String email, String code) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        User user = userRepository
                .findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        verifyCode(user, VerificationType.EMAIL, code);

        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * Verifies a login OTP against the account identified by email or phone.
     * A successful OTP does not by itself persist a trusted-device decision;
     * the caller decides whether to issue a JWT after the challenge succeeds.
     */
    @Transactional
    public User verifyLoginPhoneCode(String identifier, String code) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Email or phone is required");
        }

        String raw = identifier.trim();
        User user = userRepository.findByEmail(raw.toLowerCase(Locale.ROOT))
                .orElseGet(() -> userRepository.findByPhone(raw)
                        .orElseThrow(() -> new IllegalArgumentException("User not found")));

        verifyCode(user, VerificationType.PHONE, code);
        return user;
    }

    @Transactional
    public String createVerificationCode(User user) {
        return createEmailVerificationCode(user);
    }

    private String createCode(User user, VerificationType type) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, type)
                .ifPresent(existingCode -> {
                    retire(existingCode);
                    verificationCodeRepository.save(existingCode);
                });

        String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(1_000_000));

        VerificationCode verificationCode = VerificationCode.builder()
                .user(user)
                .code(null)
                .codeHash(passwordEncoder.encode(code))
                .type(type)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        verificationCodeRepository.save(verificationCode);
        return code;
    }

    private void verifyCode(User user, VerificationType type, String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException("Verification code must be exactly 6 digits");
        }

        VerificationCode verificationCode = verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, type)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No active verification code. Please request a new code."
                ));

        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            retire(verificationCode);
            verificationCodeRepository.save(verificationCode);
            throw new IllegalArgumentException(
                    "Verification code has expired. Please request a new code."
            );
        }

        if (!matches(verificationCode, code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        retire(verificationCode);
        verificationCodeRepository.save(verificationCode);
    }

    private boolean matches(VerificationCode verificationCode, String candidate) {
        String codeHash = verificationCode.getCodeHash();
        if (codeHash != null && !codeHash.isBlank()) {
            return passwordEncoder.matches(candidate, codeHash);
        }

        String legacyCode = verificationCode.getCode();
        if (legacyCode == null || legacyCode.isBlank()) {
            return false;
        }

        return MessageDigest.isEqual(
                legacyCode.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void retire(VerificationCode verificationCode) {
        verificationCode.setUsed(true);
        verificationCode.setCode(null);
        verificationCode.setCodeHash(null);
    }
}
