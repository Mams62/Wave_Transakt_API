package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.MerchantProviderEvent;
import com.wavetransakt.merchant.provider.MerchantAcquiringGateway;
import com.wavetransakt.merchant.repository.MerchantProviderEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/**
 * Internal provider-event ingestion boundary.
 *
 * This service intentionally has no HTTP controller. A future provider-specific
 * webhook controller may call it only after the exact provider header/signature
 * contract is known. Provider authenticity is verified before a claimed event ID
 * is trusted for replay protection. Raw provider payloads and signatures are never
 * persisted by this component.
 */
@Service
@RequiredArgsConstructor
public class ProviderEventIngressService {

    private static final int MAX_PROVIDER_CODE_LENGTH = 40;
    private static final int MAX_EVENT_KEY_LENGTH = 180;
    private static final int MAX_EVENT_TYPE_LENGTH = 80;

    private final List<MerchantAcquiringGateway> gateways;
    private final MerchantProviderEventRepository eventRepository;

    @Transactional
    public IngressResult ingest(
            String providerCode,
            MerchantAcquiringGateway.ProviderEventVerificationCommand command
    ) {
        String normalizedProvider = normalizeBounded(
                providerCode,
                "Provider code",
                MAX_PROVIDER_CODE_LENGTH
        ).toUpperCase(Locale.ROOT);

        if (command == null) {
            throw new IllegalArgumentException("Provider event is required");
        }

        int maxEventIdLength = MAX_EVENT_KEY_LENGTH - normalizedProvider.length() - 1;
        if (maxEventIdLength < 1) {
            throw new IllegalArgumentException("Provider code is too long");
        }

        String eventId = normalizeBounded(
                command.eventId(),
                "Provider event ID",
                maxEventIdLength
        );
        String eventType = normalizeBounded(
                command.eventType(),
                "Provider event type",
                MAX_EVENT_TYPE_LENGTH
        );
        byte[] payload = command.rawPayload() == null
                ? new byte[0]
                : command.rawPayload();
        String payloadHash = sha256(payload);

        MerchantAcquiringGateway gateway = gateways.stream()
                .filter(candidate -> normalizedProvider.equalsIgnoreCase(candidate.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported acquiring provider"));

        /*
         * Never trust the caller-supplied event ID for replay protection until
         * the provider adapter confirms authenticity and the verified identity.
         * Otherwise a forged request could poison a future real provider event.
         */
        MerchantAcquiringGateway.ProviderEventVerificationResult verification =
                gateway.verifyProviderEvent(command);

        boolean verified = verification != null
                && verification.verified()
                && normalizedProvider.equalsIgnoreCase(verification.provider())
                && eventId.equals(verification.eventId())
                && eventType.equals(verification.eventType());

        if (verified) {
            String eventKey = normalizedProvider + ":" + eventId;
            return reserveAndDescribe(
                    normalizedProvider,
                    eventKey,
                    eventType,
                    eventId,
                    payloadHash,
                    "VERIFIED",
                    "VERIFIED_PENDING_HANDLER",
                    null,
                    "ACCEPTED"
            );
        }

        /*
         * Rejected attempts remain auditable but are deliberately stored under
         * a separate hashed key. A forged callback can therefore never occupy
         * the trusted provider:eventId replay key used by a later genuine event.
         */
        String rejectedEventKey = normalizedProvider
                + ":REJECTED:"
                + rejectionFingerprint(
                        normalizedProvider,
                        eventId,
                        eventType,
                        command.signature(),
                        command.timestamp(),
                        payloadHash
                );

        return reserveAndDescribe(
                normalizedProvider,
                rejectedEventKey,
                eventType,
                eventId,
                payloadHash,
                "REJECTED",
                "REJECTED",
                LocalDateTime.now(),
                "REJECTED"
        );
    }

    private IngressResult reserveAndDescribe(
            String providerCode,
            String eventKey,
            String eventType,
            String providerReference,
            String payloadHash,
            String verificationStatus,
            String processingStatus,
            LocalDateTime processedAt,
            String acceptedStatus
    ) {
        int inserted = eventRepository.insertIfAbsent(
                providerCode,
                eventKey,
                eventType,
                providerReference,
                payloadHash,
                verificationStatus,
                processingStatus,
                processedAt
        );

        MerchantProviderEvent stored = eventRepository.findByEventKey(eventKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Unable to load reserved provider event"
                ));

        boolean duplicate = inserted == 0;
        return new IngressResult(
                stored.getId() == null ? null : stored.getId().toString(),
                duplicate,
                duplicate ? "DUPLICATE" : acceptedStatus,
                stored.getVerificationStatus(),
                stored.getProcessingStatus()
        );
    }

    private String rejectionFingerprint(
            String providerCode,
            String eventId,
            String eventType,
            String signature,
            String timestamp,
            String payloadHash
    ) {
        String canonical = "provider=" + providerCode
                + "\neventId=" + eventId
                + "\neventType=" + eventType
                + "\nsignature=" + safe(signature)
                + "\ntimestamp=" + safe(timestamp)
                + "\npayloadHash=" + payloadHash;
        return sha256(canonical.getBytes(StandardCharsets.UTF_8));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeBounded(
            String value,
            String label,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + " is too long");
        }
        return normalized;
    }

    private String sha256(byte[] value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record IngressResult(
            String eventRecordId,
            boolean duplicate,
            String status,
            String verificationStatus,
            String processingStatus
    ) {
    }
}
