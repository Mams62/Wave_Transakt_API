package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.dto.PosSessionDtos;
import com.wavetransakt.merchant.entity.PosPairingCode;
import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalSession;
import com.wavetransakt.merchant.exception.PosSessionAuthenticationException;
import com.wavetransakt.merchant.repository.PosPairingCodeRepository;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import com.wavetransakt.merchant.repository.PosTerminalSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosTerminalSessionService {

    private static final String PAIRING_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final PosTerminalLookupRepository terminalLookupRepository;
    private final PosPairingCodeRepository pairingCodeRepository;
    private final PosTerminalSessionRepository sessionRepository;

    @Value("${wave.pos.pairing-code-ttl-seconds:600}")
    private long pairingCodeTtlSeconds;

    @Value("${wave.pos.session-ttl-seconds:2592000}")
    private long sessionTtlSeconds;

    @Transactional
    public PosSessionDtos.PairingCodeResponse createPairingCode(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        LocalDateTime now = LocalDateTime.now();
        String pairingCode = generatePairingCode();
        LocalDateTime expiresAt = now.plusSeconds(pairingCodeTtlSeconds);

        pairingCodeRepository.save(PosPairingCode.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .createdByUserId(ownerUserId)
                .codeHash(sha256(pairingCode))
                .expiresAt(expiresAt)
                .createdAt(now)
                .build());

        return new PosSessionDtos.PairingCodeResponse(
                terminal.getTerminalCode(),
                pairingCode,
                expiresAt
        );
    }

    @Transactional
    public PosSessionDtos.TerminalSessionResponse redeem(PosSessionDtos.RedeemPairingRequest request) {
        if (request == null || request.pairingCode() == null || request.pairingCode().isBlank()) {
            throw new IllegalArgumentException("Pairing code is required");
        }

        String normalizedCode = request.pairingCode().trim().toUpperCase();
        PosPairingCode pairing = pairingCodeRepository.findByCodeHash(sha256(normalizedCode))
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired pairing code"));

        LocalDateTime now = LocalDateTime.now();
        if (pairing.getConsumedAt() != null || pairing.getExpiresAt().isBefore(now)) {
            throw new IllegalArgumentException("Invalid or expired pairing code");
        }

        pairing.setConsumedAt(now);
        pairingCodeRepository.save(pairing);

        String rawToken = generateSessionToken();
        LocalDateTime expiresAt = now.plusSeconds(sessionTtlSeconds);
        PosTerminalSession session = PosTerminalSession.builder()
                .id(UUID.randomUUID())
                .terminal(pairing.getTerminal())
                .tokenHash(sha256(rawToken))
                .deviceLabel(normalizeDeviceLabel(request.deviceLabel()))
                .expiresAt(expiresAt)
                .lastSeenAt(now)
                .createdAt(now)
                .build();
        sessionRepository.save(session);

        return new PosSessionDtos.TerminalSessionResponse(
                pairing.getTerminal().getTerminalCode(),
                rawToken,
                expiresAt
        );
    }

    @Transactional
    public PosTerminal requireValidSession(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new PosSessionAuthenticationException("POS session token is required");
        }

        PosTerminalSession session = sessionRepository.findByTokenHash(sha256(rawToken.trim()))
                .orElseThrow(() -> new PosSessionAuthenticationException("POS session is invalid or expired"));

        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            throw new PosSessionAuthenticationException("POS session is invalid or expired");
        }

        session.setLastSeenAt(now);
        sessionRepository.save(session);
        return session.getTerminal();
    }

    @Transactional
    public PosSessionDtos.RevokeSessionsResponse revokeAll(UUID ownerUserId, String terminalCode) {
        PosTerminal terminal = requireOwnedTerminal(ownerUserId, terminalCode);
        LocalDateTime now = LocalDateTime.now();
        int revoked = 0;

        for (PosTerminalSession session : sessionRepository.findAllByTerminalId(terminal.getId())) {
            if (session.getRevokedAt() == null) {
                session.setRevokedAt(now);
                sessionRepository.save(session);
                revoked++;
            }
        }

        return new PosSessionDtos.RevokeSessionsResponse(terminal.getTerminalCode(), revoked);
    }

    private PosTerminal requireOwnedTerminal(UUID ownerUserId, String terminalCode) {
        if (ownerUserId == null || terminalCode == null || terminalCode.isBlank()) {
            throw new IllegalArgumentException("Owner and terminal code are required");
        }

        PosTerminal terminal = terminalLookupRepository.findByTerminalCode(terminalCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("POS terminal not found"));

        if (terminal.getMerchant() == null
                || terminal.getMerchant().getOwner() == null
                || !ownerUserId.equals(terminal.getMerchant().getOwner().getId())) {
            throw new IllegalArgumentException("POS terminal not found");
        }
        return terminal;
    }

    private String generatePairingCode() {
        StringBuilder value = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            value.append(PAIRING_ALPHABET.charAt(SECURE_RANDOM.nextInt(PAIRING_ALPHABET.length())));
        }
        return value.toString();
    }

    private String generateSessionToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeDeviceLabel(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= 120 ? trimmed : trimmed.substring(0, 120);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
