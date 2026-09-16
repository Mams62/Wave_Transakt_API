package com.wavetransakt.verification.service;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.verification.repository.VerificationCodeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private static final String DUMMY_VERIFICATION_CODE = "000000";
    private static final String INVALID_CODE_MESSAGE =
            "Invalid or expired verification code";

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom secureRandom = new SecureRandom();

    /*
     * Used only to make unknown-account / no-code paths perform comparable
     * password-hash work to a real hashed OTP check. It is never a valid Wave
     * verification credential and is never persisted.
     */
    private volatile String dummyVerificationCodeHash;

    @PostConstruct
    void initializeDummyVerificationCodeHash() {
        dummyVerificationCodeHash = passwordEncoder.encode(DUMMY_VERIFICATION_CODE);
    }

    @Transactional
    public String createEmailVerificationCode(User user) {
        return createCode(user, VerificationType.EMAIL);
    }

    @Transactional
    public String createPhoneVerificationCode(User user) {
        return createCode(user, VerificationType.PHONE);
    }

    /**
     * Returns the same public outcome for an unknown account and for an account
     * that is already verified. This prevents the resend endpoint from becoming
     * an email-address enumeration oracle.
     */
    @Transactional
    public String resendEmailVerification(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }

        Optional<User> userOptional = userRepository
                .findByEmail(email.trim().toLowerCase(Locale.ROOT));

        if (userOptional.isEmpty()
                || Boolean.TRUE.equals(userOptional.get().getEmailVerified())) {
            consumeDummyVerificationWork(DUMMY_VERIFICATION_CODE);
            return null;
        }

        return createEmailVerificationCode(userOptional.get());
    }

    @Transactional
    public void verifyEmail(String email, String code) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required");
        }
        validateCandidateCode(code);

        Optional<User> userOptional = userRepository
                .findByEmail(email.trim().toLowerCase(Locale.ROOT));
        if (userOptional.isEmpty()) {
            consumeDummyVerificationWork(code);
            throw invalidCode();
        }

        User user = userOptional.get();
        verifyCode(user, VerificationType.EMAIL, code);

        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        userRepository.save(user);
    }

    /**
     * Verifies a login OTP against the account identified by email or phone.
     * Unknown identifiers intentionally produce the same public failure as a
     * wrong/expired OTP and still perform password-hash work.
     */
    @Transactional
    public User verifyLoginPhoneCode(String identifier, String code) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Email or phone is required");
        }
        validateCandidateCode(code);

        String raw = identifier.trim();
        Optional<User> userOptional = userRepository
                .findByEmail(raw.toLowerCase(Locale.ROOT));
        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByPhone(raw);
        }

        if (userOptional.isEmpty()) {
            consumeDummyVerificationWork(code);
            throw invalidCode();
        }

        User user = userOptional.get();
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
                .authVersion(user.getAuthVersion())
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .used(false)
                .createdAt(LocalDateTime.now())
                .build();

        verificationCodeRepository.save(verificationCode);
        return code;
    }

    private void verifyCode(User user, VerificationType type, String code) {
        validateCandidateCode(code);

        Optional<VerificationCode> codeOptional = verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, type);

        if (codeOptional.isEmpty()) {
            consumeDummyVerificationWork(code);
            throw invalidCode();
        }

        VerificationCode verificationCode = codeOptional.get();

        if (verificationCode.getAuthVersion() != user.getAuthVersion()) {
            consumeDummyVerificationWork(code);
            retire(verificationCode);
            verificationCodeRepository.save(verificationCode);
            throw invalidCode();
        }

        if (verificationCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            consumeDummyVerificationWork(code);
            retire(verificationCode);
            verificationCodeRepository.save(verificationCode);
            throw invalidCode();
        }

        if (!matches(verificationCode, code)) {
            throw invalidCode();
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
            consumeDummyVerificationWork(candidate);
            return false;
        }

        boolean legacyMatches = MessageDigest.isEqual(
                legacyCode.getBytes(StandardCharsets.UTF_8),
                candidate.getBytes(StandardCharsets.UTF_8)
        );

        /*
         * Legacy plaintext rows exist only for migration compatibility, but do
         * comparable BCrypt work so their temporary presence does not create an
         * obvious timing distinction from the hashed-code path.
         */
        consumeDummyVerificationWork(candidate);
        return legacyMatches;
    }

    private void validateCandidateCode(String code) {
        if (code == null || !code.matches("\\d{6}")) {
            throw new IllegalArgumentException(
                    "Verification code must be exactly 6 digits"
            );
        }
    }

    private void consumeDummyVerificationWork(String candidate) {
        String hash = dummyVerificationCodeHash();
        if (hash == null || hash.isBlank()) {
            /* Unit-test/misconfigured-encoder fallback; production BCrypt does not return null. */
            passwordEncoder.encode(candidate);
            return;
        }
        passwordEncoder.matches(candidate, hash);
    }

    private String dummyVerificationCodeHash() {
        String current = dummyVerificationCodeHash;
        if (current != null && !current.isBlank()) {
            return current;
        }

        synchronized (this) {
            if (dummyVerificationCodeHash == null || dummyVerificationCodeHash.isBlank()) {
                dummyVerificationCodeHash = passwordEncoder.encode(DUMMY_VERIFICATION_CODE);
            }
            return dummyVerificationCodeHash;
        }
    }

    private IllegalArgumentException invalidCode() {
        return new IllegalArgumentException(INVALID_CODE_MESSAGE);
    }

    private void retire(VerificationCode verificationCode) {
        verificationCode.setUsed(true);
        verificationCode.setCode(null);
        verificationCode.setCodeHash(null);
    }
}
