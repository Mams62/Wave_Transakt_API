package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.AuthResponse;
import com.wavetransakt.auth.dto.LoginOtpRequest;
import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
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

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final WalletService walletService;
    private final VerificationService verificationService;
    private final SmsOtpSender smsOtpSender;
    private final FaceLoginChallengeService faceLoginChallengeService;
    private final DojahGovernmentIdentityClient governmentIdentityClient;

    @Value("${wave.demo.return-verification-code:false}")
    private boolean returnVerificationCode;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        String phone = request.getPhone().trim();
        String bvn = request.getBvn().trim();
        String nin = request.getNin().trim();

        if (userRepository.existsByEmail(email)) throw new IllegalArgumentException("Email already registered");
        if (userRepository.existsByPhone(phone)) throw new IllegalArgumentException("Phone number already registered");
        if (userRepository.existsByBvn(bvn)) throw new IllegalArgumentException("BVN already linked to an account");
        if (userRepository.existsByNin(nin)) throw new IllegalArgumentException("NIN already linked to an account");

        // Fail closed: registration is not allowed to continue from format-only checks.
        // Both identifiers must resolve through the configured government identity provider.
        DojahGovernmentIdentityClient.IdentityRecord bvnRecord = governmentIdentityClient.lookupBvn(bvn);
        DojahGovernmentIdentityClient.IdentityRecord ninRecord = governmentIdentityClient.lookupNin(nin);

        requireIdentityMatch("BVN", bvnRecord, request);
        requireIdentityMatch("NIN", ninRecord, request);
        requireSamePerson(bvnRecord, ninRecord);

        User user = User.builder()
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .email(email)
                .phone(phone)
                .password(passwordEncoder.encode(request.getAccountPin()))
                .bvn(bvn)
                .nin(nin)
                .bvnVerified(true)
                .ninVerified(true)
                .governmentIdentityVerifiedAt(LocalDateTime.now())
                .state(request.getState().trim())
                .localGovernment(request.getLocalGovernment().trim())
                .dateOfBirth(request.getDateOfBirth())
                .gender(request.getGender().trim())
                .transactionPinHash(passwordEncoder.encode(request.getTransactionPin()))
                .build();

        user = userRepository.save(user);
        walletService.createWallet(user);

        String verificationCode = verificationService.createEmailVerificationCode(user);

        return AuthResponse.builder()
                .message("Identity verified. Registration successful. Please verify your email.")
                .verificationCode(returnVerificationCode ? verificationCode : null)
                .requiresOtp(false)
                .faceVerificationRequired(false)
                .build();
    }

    /** Starts credential login. No authenticated JWT is issued here. */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = requireUserByIdentifier(request.getIdentifier());

        if (!passwordEncoder.matches(request.getAccountPin(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid email/phone or account PIN");
        }
        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is not active. Please verify your email.");
        }

        String otp = verificationService.createPhoneVerificationCode(user);
        boolean delivered = smsOtpSender.sendLoginOtp(user.getPhone(), otp);

        if (!delivered && !returnVerificationCode) {
            throw new IllegalStateException("Phone verification is temporarily unavailable. Please try again later.");
        }

        return AuthResponse.builder()
                .message(delivered
                        ? "A 6-digit login code was sent to your registered phone number."
                        : "Controlled-stage login code generated. Configure the SMS provider before production.")
                .verificationCode(returnVerificationCode ? otp : null)
                .requiresOtp(true)
                .maskedPhone(maskPhone(user.getPhone()))
                .faceVerificationRequired(false)
                .build();
    }

    /**
     * OTP success is now only the second factor. It creates a short-lived opaque
     * pre-auth challenge; it never returns a wallet/API JWT.
     */
    @Transactional
    public AuthResponse verifyLoginOtp(LoginOtpRequest request) {
        User user = verificationService.verifyLoginPhoneCode(request.getIdentifier(), request.getOtp());

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is not active. Please verify your email.");
        }
        if (user.getNin() == null || user.getNin().isBlank() || !Boolean.TRUE.equals(user.getNinVerified())) {
            throw new IllegalArgumentException("Identity onboarding is incomplete for this account. Complete verified NIN onboarding before using secure face login.");
        }

        FaceLoginChallengeService.IssuedChallenge challenge = faceLoginChallengeService.issue(user);

        return AuthResponse.builder()
                .message("Phone verified. Complete live face verification to finish signing in.")
                .token(null)
                .requiresOtp(false)
                .faceVerificationRequired(true)
                .faceChallengeToken(challenge.token())
                .build();
    }

    private void requireIdentityMatch(
            String source,
            DojahGovernmentIdentityClient.IdentityRecord record,
            RegisterRequest request
    ) {
        if (!sameName(record.firstName(), request.getFirstName())
                || !sameName(record.lastName(), request.getLastName())) {
            throw new IllegalArgumentException(source + " identity does not match the entered name");
        }

        if (record.dateOfBirth() == null || !record.dateOfBirth().equals(request.getDateOfBirth())) {
            throw new IllegalArgumentException(source + " identity does not match the entered date of birth");
        }

        if (record.gender() != null && !record.gender().isBlank()
                && request.getGender() != null && !request.getGender().isBlank()
                && !normalizeGender(record.gender()).equals(normalizeGender(request.getGender()))) {
            throw new IllegalArgumentException(source + " identity does not match the entered gender");
        }
    }

    private void requireSamePerson(
            DojahGovernmentIdentityClient.IdentityRecord bvn,
            DojahGovernmentIdentityClient.IdentityRecord nin
    ) {
        boolean same = sameName(bvn.firstName(), nin.firstName())
                && sameName(bvn.lastName(), nin.lastName())
                && bvn.dateOfBirth() != null
                && bvn.dateOfBirth().equals(nin.dateOfBirth());

        if (!same) {
            throw new IllegalArgumentException("BVN and NIN do not belong to the same verified identity");
        }
    }

    private boolean sameName(String left, String right) {
        return normalizeName(left).equals(normalizeName(right));
    }

    private String normalizeName(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return decomposed
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]", "")
                .trim();
    }

    private String normalizeGender(String value) {
        if (value == null) return "";
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.equals("M")) return "MALE";
        if (normalized.equals("F")) return "FEMALE";
        return normalized;
    }

    private User requireUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Email or phone is required");
        }

        String raw = identifier.trim();
        return userRepository.findByEmail(raw.toLowerCase(Locale.ROOT))
                .orElseGet(() -> userRepository.findByPhone(raw)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid email/phone or account PIN")));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.isBlank()) return "registered phone";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.length() <= 4) return "****";
        return "***" + digits.substring(digits.length() - 4);
    }
}
