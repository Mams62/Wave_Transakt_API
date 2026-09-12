package com.wavetransakt.identity.service;

import com.wavetransakt.identity.dto.LivenessCaptureResponse;
import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.provider.DojahLivenessClient;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LivenessService {

    private final LivenessSessionRepository repository;
    private final DojahLivenessClient dojahLivenessClient;

    @Value("${wave.identity.liveness.provider:UNCONFIGURED}")
    private String provider;

    @Value("${wave.identity.liveness.webhook-secret:}")
    private String webhookSecret;

    @Value("${wave.identity.liveness.provider-configured:false}")
    private boolean providerConfigured;

    @Transactional
    public LivenessSessionResponse start(User user, String purpose) {
        String normalizedPurpose = purpose == null || purpose.isBlank()
                ? "LOGIN"
                : purpose.trim().toUpperCase(Locale.ROOT);

        if (!normalizedPurpose.equals("LOGIN") && !normalizedPurpose.equals("ENROLLMENT")) {
            throw new IllegalArgumentException("Unsupported liveness purpose");
        }

        boolean ready = providerConfigured && dojahLivenessClient.isConfigured();

        LivenessSession session = LivenessSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(provider == null || provider.isBlank() ? "UNCONFIGURED" : provider.trim())
                .status(LivenessStatus.PENDING)
                .purpose(normalizedPurpose)
                .providerMessage(ready
                        ? "Liveness session created. Capture a live selfie to continue."
                        : "Liveness provider credentials are not configured on the server yet.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(session);
        return LivenessSessionResponse.from(session, ready);
    }

    @Transactional
    public LivenessCaptureResponse capture(
            User user,
            UUID sessionId,
            String imageBase64
    ) {
        LivenessSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Liveness session not found");
        }

        if (!providerConfigured || !dojahLivenessClient.isConfigured()) {
            throw new IllegalStateException("Dojah liveness is not configured on the server");
        }

        if (session.getStatus() == LivenessStatus.VERIFIED) {
            throw new IllegalStateException("Liveness session is already verified");
        }
        if (session.getStatus() == LivenessStatus.REJECTED ||
                session.getStatus() == LivenessStatus.EXPIRED) {
            throw new IllegalStateException("Liveness session is no longer active");
        }

        session.setStatus(LivenessStatus.IN_PROGRESS);
        session.setUpdatedAt(LocalDateTime.now());
        repository.save(session);

        try {
            DojahLivenessClient.Result result = dojahLivenessClient.check(imageBase64);

            if (result.passed()) {
                // Passing Dojah liveness proves a live face was present, but it does
                // not by itself prove the face belongs to this Wave account. Keep
                // the session IN_PROGRESS until the identity-match step completes.
                session.setStatus(LivenessStatus.IN_PROGRESS);
                session.setProviderMessage(
                        "Liveness passed. Identity face match is still required before this session can be verified."
                );
            } else {
                session.setStatus(LivenessStatus.REJECTED);
                session.setProviderMessage(
                        result.multifaceDetected()
                                ? "Liveness rejected because multiple faces were detected."
                                : "Liveness check failed. Capture a fresh live selfie and try again."
                );
            }

            session.setUpdatedAt(LocalDateTime.now());
            repository.save(session);

            return new LivenessCaptureResponse(
                    session.getId(),
                    session.getProvider(),
                    session.getStatus().name(),
                    result.probability(),
                    result.faceDetected(),
                    result.multifaceDetected(),
                    result.passed(),
                    result.passed(),
                    session.getProviderMessage()
            );
        } catch (RuntimeException e) {
            session.setStatus(LivenessStatus.ERROR);
            session.setProviderMessage("Liveness provider request failed. No identity verification was recorded.");
            session.setUpdatedAt(LocalDateTime.now());
            repository.save(session);
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse status(User user, UUID sessionId) {
        LivenessSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Liveness session not found");
        }

        return LivenessSessionResponse.from(
                session,
                providerConfigured && dojahLivenessClient.isConfigured()
        );
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse latest(User user) {
        LivenessSession session = repository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No liveness session found"));
        return LivenessSessionResponse.from(
                session,
                providerConfigured && dojahLivenessClient.isConfigured()
        );
    }

    @Transactional
    public void applyProviderResult(
            String suppliedSecret,
            UUID sessionId,
            String providerSessionId,
            String status,
            String message
    ) {
        requireValidWebhookSecret(suppliedSecret);

        LivenessSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));

        LivenessStatus mappedStatus;
        try {
            mappedStatus = LivenessStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid liveness status");
        }

        if (mappedStatus == LivenessStatus.VERIFIED &&
                (!providerConfigured || !dojahLivenessClient.isConfigured())) {
            throw new IllegalStateException("Liveness provider is not configured");
        }

        session.setProviderSessionId(providerSessionId == null || providerSessionId.isBlank()
                ? session.getProviderSessionId()
                : providerSessionId.trim());
        session.setStatus(mappedStatus);
        session.setProviderMessage(message == null ? null : message.trim());
        session.setUpdatedAt(LocalDateTime.now());
        if (mappedStatus == LivenessStatus.VERIFIED) {
            session.setVerifiedAt(LocalDateTime.now());
        }
        repository.save(session);
    }

    private void requireValidWebhookSecret(String suppliedSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalStateException("Liveness webhook secret is not configured");
        }
        byte[] expected = webhookSecret.getBytes(StandardCharsets.UTF_8);
        byte[] supplied = (suppliedSecret == null ? "" : suppliedSecret)
                .getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, supplied)) {
            throw new IllegalArgumentException("Invalid liveness webhook signature");
        }
    }
}
