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
                .restrictedOnboarding(false)
                .identityVerificationRequired(false)
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
                .restrictedOnboarding(false)
                .identityVerificationRequired(false)
                .build();
    }

    /**
     * OTP success is only the second factor. Legacy accounts that already have a
     * stored NIN but pre-date verified onboarding are upgraded here only after
     * the government provider confirms that NIN matches the account profile.
     * No JWT is issued until the subsequent live-face verification succeeds.
     *
     * When the external government-identity provider is unavailable, the server
     * returns an explicit restricted-onboarding state instead of issuing a JWT.
     * That state is only for a local setup experience and cannot access protected
     * wallet, transfer, funding, QR, or payment APIs.
     */
    @Transactional
    public AuthResponse verifyLoginOtp(LoginOtpRequest request) {
        User user = verificationService.verifyLoginPhoneCode(request.getIdentifier(), request.getOtp());

        if (!user.isEnabled()) {
            throw new IllegalArgumentException("Account is not active. Please verify your email.");
        }

        try {
            ensureVerifiedNinForSecureLogin(user);
        } catch (IllegalStateException providerUnavailable) {
            return AuthResponse.builder()
                    .message("Phone verified. Identity verification is temporarily unavailable. You may enter setup mode, but all financial services remain locked until NIN and live-face verification are completed.")
                    .token(null)
                    .requiresOtp(false)
                    .faceVerificationRequired(false)
                    .restrictedOnboarding(true)
                    .identityVerificationRequired(true)
                    .faceChallengeToken(null)
                    .build();
        }

        FaceLoginChallengeService.IssuedChallenge challenge = faceLoginChallengeService.issue(user);

        return AuthResponse.builder()
                .message("Phone and government identity verified. Complete live face verification to finish signing in.")
                .token(null)
                .requiresOtp(false)
                .faceVerificationRequired(true)
                .restrictedOnboarding(false)
                .identityVerificationRequired(false)
                .faceChallengeToken(challenge.token())
                .build();
    }

    private void ensureVerifiedNinForSecureLogin(User user) {
        if (Boolean.TRUE.equals(user.getNinVerified())) {
            return;
        }

        if (user.getNin() == null || user.getNin().isBlank()) {
            throw new IllegalArgumentException(
                    "Identity onboarding is incomplete for this account. A verified NIN is required before secure face login."
            );
        }

        DojahGovernmentIdentityClient.IdentityRecord ninRecord = governmentIdentityClient.lookupNin(user.getNin());

        if (!sameName(ninRecord.firstName(), user.getFirstName())
                || !sameName(ninRecord.lastName(), user.getLastName())) {
            throw new IllegalArgumentException("The NIN on this account does not match the account holder name.");
        }

        if (user.getDateOfBirth() == null
                || ninRecord.dateOfBirth() == null
                || !ninRecord.dateOfBirth().equals(user.getDateOfBirth())) {
            throw new IllegalArgumentException("The NIN on this account does not match the account holder date of birth.");
        }

        if (ninRecord.gender() != null && !ninRecord.gender().isBlank()
                && user.getGender() != null && !user.getGender().isBlank()
                && !normalizeGender(ninRecord.gender()).equals(normalizeGender(user.getGender()))) {
            throw new IllegalArgumentException("The NIN on this account does not match the account holder gender.");
        }

        user.setNinVerified(true);
        user.setGovernmentIdentityVerifiedAt(LocalDateTime.now());
        userRepository.save(user);
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
