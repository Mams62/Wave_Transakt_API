package com.wavetransakt.identity.service;

import com.wavetransakt.identity.dto.LivenessCaptureResponse;
import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.provider.DojahLivenessClient;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LivenessService {
    static final int MAX_SELFIE_BYTES = 4 * 1024 * 1024;
    static final int MAX_SELFIE_BASE64_CHARS = ((MAX_SELFIE_BYTES + 2) / 3) * 4;
    private static final int MAX_DATA_URL_PREFIX_CHARS = 128;

    private final LivenessSessionRepository repository;
    private final DojahLivenessClient dojahLivenessClient;
    private final UserRepository userRepository;

    @Value("${wave.identity.liveness.provider:UNCONFIGURED}") private String provider;
    @Value("${wave.identity.liveness.webhook-secret:}") private String webhookSecret;
    @Value("${wave.identity.liveness.provider-configured:false}") private boolean providerConfigured;

    @Transactional
    public LivenessSessionResponse start(User user, String purpose) {
        String normalizedPurpose = purpose == null || purpose.isBlank() ? "LOGIN" : purpose.trim().toUpperCase(Locale.ROOT);
        if (!normalizedPurpose.equals("LOGIN") && !normalizedPurpose.equals("ENROLLMENT")) throw new IllegalArgumentException("Unsupported liveness purpose");
        boolean ready = providerConfigured && dojahLivenessClient.isConfigured();
        LivenessSession session = LivenessSession.builder()
                .id(UUID.randomUUID()).user(user)
                .provider(provider == null || provider.isBlank() ? "UNCONFIGURED" : provider.trim())
                .status(LivenessStatus.PENDING).purpose(normalizedPurpose)
                .providerMessage(ready ? "Capture a live selfie to verify liveness and account identity." : "Liveness provider credentials are not configured on the server yet.")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        repository.save(session);
        return LivenessSessionResponse.from(session, ready);
    }

    @Transactional
    public LivenessCaptureResponse capture(User user, UUID sessionId, String imageBase64) {
        String image = normalizeAndValidateSelfie(imageBase64);

        /*
         * Hold a row-level lock for the session while provider verification is
         * in progress. This is intentionally scoped to one liveness session so
         * duplicate concurrent captures cannot fan out into duplicate paid
         * provider calls across API instances.
         */
        LivenessSession session = repository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));
        if (!session.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Liveness session not found");
        if (!providerConfigured || !dojahLivenessClient.isConfigured()) throw new IllegalStateException("Dojah identity verification is not configured on the server");
        if (session.getStatus() == LivenessStatus.VERIFIED) throw new IllegalStateException("Liveness session is already verified");
        if (session.getStatus() == LivenessStatus.REJECTED || session.getStatus() == LivenessStatus.EXPIRED) throw new IllegalStateException("Liveness session is no longer active");
        if (session.getStatus() == LivenessStatus.IN_PROGRESS) throw new IllegalStateException("Liveness verification is already in progress");

        session.setStatus(LivenessStatus.IN_PROGRESS);
        session.setUpdatedAt(LocalDateTime.now());
        repository.save(session);

        try {
            DojahLivenessClient.Result live = dojahLivenessClient.check(image);
            if (!live.passed()) {
                session.setStatus(LivenessStatus.REJECTED);
                session.setProviderMessage(live.multifaceDetected() ? "Verification rejected because multiple faces were detected." : "Live-face verification failed. Capture a fresh selfie and try again.");
                session.setUpdatedAt(LocalDateTime.now());
                repository.save(session);
                return response(session, live, false, "Live-face verification failed.");
            }

            DojahLivenessClient.IdentityMatchResult identity = dojahLivenessClient.verifySelfieNin(user.getNin(), image);
            if (identity.match()) {
                LocalDateTime verifiedAt = LocalDateTime.now();
                session.setStatus(LivenessStatus.VERIFIED);
                session.setVerifiedAt(verifiedAt);
                session.setProviderMessage("Live face matched the verified account identity.");

                if ("ENROLLMENT".equalsIgnoreCase(session.getPurpose())) {
                    user.setFaceIdentityEnrolled(true);
                    user.setFaceIdentityEnrolledAt(verifiedAt);
                    userRepository.save(user);
                    session.setProviderMessage("Face ID enrollment completed and matched the verified account identity.");
                }
            } else {
                session.setStatus(LivenessStatus.REJECTED);
                session.setProviderMessage("Live face did not match the identity bound to this account.");
            }
            session.setUpdatedAt(LocalDateTime.now());
            repository.save(session);
            return response(session, live, identity.match(), session.getProviderMessage());
        } catch (RuntimeException e) {
            session.setStatus(LivenessStatus.ERROR);
            session.setProviderMessage("Identity provider request failed. No identity verification was recorded.");
            session.setUpdatedAt(LocalDateTime.now());
            repository.save(session);
            throw e;
        }
    }

    private String normalizeAndValidateSelfie(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new IllegalArgumentException("Selfie image is required");
        }

        String image = imageBase64.trim();
        if (image.length() > MAX_SELFIE_BASE64_CHARS + MAX_DATA_URL_PREFIX_CHARS) {
            throw new IllegalArgumentException("Selfie image is too large");
        }

        int comma = image.indexOf(',');
        if (image.startsWith("data:image/") && comma >= 0) {
            image = image.substring(comma + 1).trim();
        }

        if (image.isBlank()) {
            throw new IllegalArgumentException("Selfie image is required");
        }
        if (image.length() > MAX_SELFIE_BASE64_CHARS) {
            throw new IllegalArgumentException("Selfie image is too large");
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(image);
        } catch (IllegalArgumentException invalidBase64) {
            throw new IllegalArgumentException("Selfie image must be valid Base64");
        }

        try {
            if (decoded.length == 0) {
                throw new IllegalArgumentException("Selfie image is required");
            }
            if (decoded.length > MAX_SELFIE_BYTES) {
                throw new IllegalArgumentException("Selfie image is too large");
            }
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }

        return image;
    }

    private LivenessCaptureResponse response(LivenessSession session, DojahLivenessClient.Result live, boolean identityMatched, String message) {
        return new LivenessCaptureResponse(session.getId(), session.getProvider(), session.getStatus().name(), live.probability(), live.faceDetected(), live.multifaceDetected(), live.passed(), live.passed() && !identityMatched, message);
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse status(User user, UUID sessionId) {
        LivenessSession session = repository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));
        if (!session.getUser().getId().equals(user.getId())) throw new IllegalArgumentException("Liveness session not found");
        return LivenessSessionResponse.from(session, providerConfigured && dojahLivenessClient.isConfigured());
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse latest(User user) {
        LivenessSession session = repository.findTopByUserIdOrderByCreatedAtDesc(user.getId()).orElseThrow(() -> new IllegalArgumentException("No liveness session found"));
        return LivenessSessionResponse.from(session, providerConfigured && dojahLivenessClient.isConfigured());
    }

    @Transactional
    public void applyProviderResult(String suppliedSecret, UUID sessionId, String providerSessionId, String status, String message) {
        requireValidWebhookSecret(suppliedSecret);
        LivenessSession session = repository.findById(sessionId).orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));
        LivenessStatus mappedStatus;
        try { mappedStatus = LivenessStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid liveness status"); }
        if (mappedStatus == LivenessStatus.VERIFIED && (!providerConfigured || !dojahLivenessClient.isConfigured())) throw new IllegalStateException("Liveness provider is not configured");
        session.setProviderSessionId(providerSessionId == null || providerSessionId.isBlank() ? session.getProviderSessionId() : providerSessionId.trim());
        session.setStatus(mappedStatus);
        session.setProviderMessage(message == null ? null : message.trim());
        session.setUpdatedAt(LocalDateTime.now());
        if (mappedStatus == LivenessStatus.VERIFIED) session.setVerifiedAt(LocalDateTime.now());
        repository.save(session);
    }

    private void requireValidWebhookSecret(String suppliedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) throw new IllegalStateException("Liveness webhook secret is not configured");
        byte[] expected = webhookSecret.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedSecret == null ? "" : suppliedSecret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) throw new IllegalArgumentException("Invalid liveness webhook signature");
    }
}
