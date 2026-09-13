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
 * contract is known. Events are deduplicated and payloads are stored only as a
 * SHA-256 hash here; raw provider payloads are not persisted by this component.
 */
@Service
@RequiredArgsConstructor
public class ProviderEventIngressService {

    private final List<MerchantAcquiringGateway> gateways;
    private final MerchantProviderEventRepository eventRepository;

    @Transactional
    public IngressResult ingest(String providerCode, MerchantAcquiringGateway.ProviderEventVerificationCommand command) {
        String normalizedProvider = normalizeRequired(providerCode, "Provider code").toUpperCase(Locale.ROOT);
        if (command == null) {
            throw new IllegalArgumentException("Provider event is required");
        }

        String eventId = normalizeRequired(command.eventId(), "Provider event ID");
        String eventType = normalizeRequired(command.eventType(), "Provider event type");
        byte[] payload = command.rawPayload() == null ? new byte[0] : command.rawPayload();
        String eventKey = normalizedProvider + ":" + eventId;

        MerchantProviderEvent existing = eventRepository.findByEventKey(eventKey).orElse(null);
        if (existing != null) {
            return new IngressResult(existing.getId() == null ? null : existing.getId().toString(), true,
                    "DUPLICATE", existing.getVerificationStatus(), existing.getProcessingStatus());
        }

        MerchantAcquiringGateway gateway = gateways.stream()
                .filter(candidate -> normalizedProvider.equalsIgnoreCase(candidate.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported acquiring provider"));

        MerchantAcquiringGateway.ProviderEventVerificationResult verification = gateway.verifyProviderEvent(command);
        boolean verified = verification != null
                && verification.verified()
                && eventId.equals(verification.eventId())
                && eventType.equals(verification.eventType());

        LocalDateTime now = LocalDateTime.now();
        MerchantProviderEvent event = MerchantProviderEvent.builder()
                .providerCode(normalizedProvider)
                .eventKey(eventKey)
                .eventType(eventType)
                .providerReference(eventId)
                .payloadHash(sha256(payload))
                .verificationStatus(verified ? "VERIFIED" : "REJECTED")
                .processingStatus(verified ? "VERIFIED_PENDING_HANDLER" : "REJECTED")
                .receivedAt(now)
                .processedAt(verified ? null : now)
                .build();

        event = eventRepository.save(event);

        return new IngressResult(
                event.getId() == null ? null : event.getId().toString(),
                false,
                verified ? "ACCEPTED" : "REJECTED",
                event.getVerificationStatus(),
                event.getProcessingStatus()
        );
    }

    private String normalizeRequired(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
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
