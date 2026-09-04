package com.wavetransakt.verification.service;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.verification.repository.VerificationCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class VerificationService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new 6-digit email verification code.
     */
    @Transactional
    public String createEmailVerificationCode(User user) {

        // Mark any previous unused email verification code as used.
        verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                        user,
                        VerificationType.EMAIL
                )
                .ifPresent(existingCode -> {

                    existingCode.setUsed(true);

                    verificationCodeRepository.save(existingCode);
                });

        // Generate a secure 6-digit code.
        String code = String.format(
                "%06d",
                secureRandom.nextInt(1_000_000)
        );

        // Create verification record.
        VerificationCode verificationCode =
                VerificationCode.builder()
                        .user(user)
                        .code(code)
                        .type(VerificationType.EMAIL)
                        .expiresAt(
                                LocalDateTime.now().plusMinutes(10)
                        )
                        .used(false)
                        .createdAt(LocalDateTime.now())
                        .build();

        // IMPORTANT: Save the code to PostgreSQL.
        verificationCodeRepository.save(verificationCode);

        return code;
    }

    /**
     * Verify a user's email using the latest unused code.
     */
    @Transactional
    public void verifyEmail(
            String email,
            String code
    ) {

        // Find user.
        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );

        // Find latest unused email verification code.
        VerificationCode verificationCode =
                verificationCodeRepository
                        .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                                user,
                                VerificationType.EMAIL
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "No active email verification code. Please request a new code."
                                )
                        );

        // Check expiration.
        if (verificationCode.getExpiresAt()
                .isBefore(LocalDateTime.now())) {

            verificationCode.setUsed(true);

            verificationCodeRepository.save(
                    verificationCode
            );

            throw new IllegalArgumentException(
                    "Verification code has expired. Please request a new code."
            );
        }

        // Check code.
        if (!verificationCode.getCode().equals(code)) {

            throw new IllegalArgumentException(
                    "Invalid verification code"
            );
        }

        // Mark verification code as used.
        verificationCode.setUsed(true);

        // Verify email.
        user.setEmailVerified(true);

        // Activate account.
        user.setAccountStatus(
                AccountStatus.ACTIVE
        );

        // Save both changes.
        verificationCodeRepository.save(
                verificationCode
        );

        userRepository.save(user);
    }

    /**
     * Compatibility method.
     *
     * If any other part of the application calls
     * createVerificationCode(), it will still work.
     */
    @Transactional
    public String createVerificationCode(User user) {

        return createEmailVerificationCode(user);
    }
}