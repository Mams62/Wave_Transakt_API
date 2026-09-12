package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.ForgotPasswordRequest;
import com.wavetransakt.auth.dto.ResetPasswordRequest;
import com.wavetransakt.auth.entity.PasswordResetToken;
import com.wavetransakt.auth.repository.PasswordResetTokenRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.service.SmsOtpSender;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final SmsOtpSender smsOtpSender;

    @Value("${wave.demo.return-verification-code:false}")
    private boolean returnVerificationCode;

    /**
     * Starts account-PIN recovery. For an unknown identifier this intentionally
     * returns the same public response so the endpoint cannot be used to enumerate
     * Wave accounts. The raw code is delivered only to the registered phone and
     * is never persisted; controlled staging may return it when explicitly enabled.
     */
    @Transactional
    public String requestPasswordReset(ForgotPasswordRequest request) {
        Optional<User> userOptional = findUserOptional(request.getIdentifier());
        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();
        passwordResetTokenRepository.deleteByUser(user);

        String code = generateCode();
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .code(passwordEncoder.encode(code))
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .used(false)
                .build();
        passwordResetTokenRepository.save(token);

        boolean delivered = smsOtpSender.sendAccountRecoveryOtp(user.getPhone(), code);
        if (!delivered && !returnVerificationCode) {
            throw new IllegalStateException(
                    "Account PIN recovery is temporarily unavailable. Please try again later."
            );
        }

        return returnVerificationCode ? code : null;
    }

    /** Resets only the account-login PIN; the transaction PIN is not changed. */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        User user = findUserOptional(request.getIdentifier())
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired recovery code"));

        PasswordResetToken token = passwordResetTokenRepository
                .findTopByUserAndUsedFalseOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired recovery code"));

        if (!token.isValid() || !passwordEncoder.matches(request.getCode(), token.getCode())) {
            throw new IllegalArgumentException("Invalid or expired recovery code");
        }

        String newPin = request.getNewPassword();
        if (passwordEncoder.matches(newPin, user.getPassword())) {
            throw new IllegalArgumentException("Choose a different account PIN from your current PIN");
        }

        user.setPassword(passwordEncoder.encode(newPin));
        userRepository.save(user);

        token.setUsed(true);
        passwordResetTokenRepository.save(token);
    }

    private Optional<User> findUserOptional(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String value = identifier.trim();
        Optional<User> byEmail = userRepository.findByEmail(value.toLowerCase(Locale.ROOT));
        return byEmail.isPresent() ? byEmail : userRepository.findByPhone(value);
    }

    private String generateCode() {
        return String.format(Locale.ROOT, "%06d", SECURE_RANDOM.nextInt(1_000_000));
    }
}
