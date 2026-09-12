package com.wavetransakt.identity.dto;

import com.wavetransakt.identity.entity.LivenessSession;

import java.time.LocalDateTime;
import java.util.UUID;

public record LivenessSessionResponse(
        UUID sessionId,
        String provider,
        String providerSessionId,
        String status,
        String purpose,
        String message,
        boolean providerConfigured,
        LocalDateTime createdAt,
        LocalDateTime verifiedAt
) {
    public static LivenessSessionResponse from(
            LivenessSession session,
            boolean providerConfigured
    ) {
        return new LivenessSessionResponse(
                session.getId(),
                session.getProvider(),
                session.getProviderSessionId(),
                session.getStatus().name(),
                session.getPurpose(),
                session.getProviderMessage(),
                providerConfigured,
                session.getCreatedAt(),
                session.getVerifiedAt()
        );
    }
}
