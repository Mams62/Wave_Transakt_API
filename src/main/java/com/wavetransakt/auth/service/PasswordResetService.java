package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.ForgotPasswordRequest;
import com.wavetransakt.auth.dto.ResetPasswordRequest;
import com.wavetransakt.auth.entity.PasswordResetToken;
import com.wavetransakt.auth.repository.PasswordResetTokenRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private final UserRepository userRepository;

    private final PasswordResetTokenRepository
            passwordResetTokenRepository;

    private final PasswordEncoder passwordEncoder;

    /**
     * Create a password reset code.
     *
     * DEMO MODE:
     * The generated code is returned to the caller.
     *
     * Production should send this code by
     * email/SMS instead.
     */
    @Transactional
    public String requestPasswordReset(
            ForgotPasswordRequest request
    ) {

        User user = findUser(
                request.getIdentifier()
        );

        /*
         * Remove previous reset codes for this user.
         */
        passwordResetTokenRepository
                .deleteByUser(user);

        String code = generateCode();

        PasswordResetToken token =
                PasswordResetToken.builder()
                        .user(user)
                        .code(code)
                        .expiresAt(
                                LocalDateTime.now()
                                        .plusMinutes(15)
                        )
                        .used(false)
                        .build();

        passwordResetTokenRepository.save(token);

        return code;
    }

    /**
     * Reset the user's password.
     */
    @Transactional
    public void resetPassword(
            ResetPasswordRequest request
    ) {

        User user = findUser(
                request.getIdentifier()
        );

        PasswordResetToken token =
                passwordResetTokenRepository
                        .findByUserAndCodeAndUsedFalse(
                                user,
                                request.getCode()
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Invalid reset code"
                                )
                        );

        if (token.isExpired()) {

            throw new IllegalArgumentException(
                    "Reset code has expired"
            );
        }

        if (token.isUsed()) {

            throw new IllegalArgumentException(
                    "Reset code has already been used"
            );
        }

        user.setPassword(
                passwordEncoder.encode(
                        request.getNewPassword()
                )
        );

        userRepository.save(user);

        token.setUsed(true);

        passwordResetTokenRepository.save(token);
    }

    private User findUser(
            String identifier
    ) {

        if (identifier == null ||
                identifier.isBlank()) {

            throw new IllegalArgumentException(
                    "Email or phone is required"
            );
        }

        return userRepository
                .findByEmail(identifier)
                .orElseGet(() ->
                        userRepository
                                .findByPhone(identifier)
                                .orElseThrow(() ->
                                        new IllegalArgumentException(
                                                "User not found"
                                        )
                                )
                );
    }

    private String generateCode() {

        return String.format(
                "%06d",
                ThreadLocalRandom.current()
                        .nextInt(0, 1_000_000)
        );
    }
}