package com.wavetransakt.identity.service;

import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
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

        LivenessSession session = LivenessSession.builder()
                .id(UUID.randomUUID())
                .user(user)
                .provider(provider == null || provider.isBlank() ? "UNCONFIGURED" : provider.trim())
                .status(LivenessStatus.PENDING)
                .purpose(normalizedPurpose)
                .providerMessage(providerConfigured
                        ? "Liveness session created. Complete verification with the configured provider."
                        : "Liveness provider is not configured on the server yet.")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        repository.save(session);
        return LivenessSessionResponse.from(session, providerConfigured);
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse status(User user, UUID sessionId) {
        LivenessSession session = repository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));

        if (!session.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("Liveness session not found");
        }

        return LivenessSessionResponse.from(session, providerConfigured);
    }

    @Transactional(readOnly = true)
    public LivenessSessionResponse latest(User user) {
        LivenessSession session = repository.findTopByUserIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("No liveness session found"));
        return LivenessSessionResponse.from(session, providerConfigured);
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

        if (mappedStatus == LivenessStatus.VERIFIED && !providerConfigured) {
            throw new IllegalStateException("Liveness provider is not configured");
        }

        session.setProviderSessionId(providerSessionId == null || providerSessionId.isBlank()
                ? session.getProviderSessionId()
                : providerSessionId.trim());
        session.setStatus(mappedStatus);
        session.setProviderMessage(message == null ? null : message.trim());
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
