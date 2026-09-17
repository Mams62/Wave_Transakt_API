package com.wavetransakt.auth.service;

import com.wavetransakt.auth.entity.FaceLoginChallenge;
import com.wavetransakt.auth.repository.FaceLoginChallengeRepository;
import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.security.JwtService;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
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
public class FaceLoginChallengeService {

    private static final int TOKEN_BYTES = 32;
    private static final int CHALLENGE_MINUTES = 10;

    private final FaceLoginChallengeRepository repository;
    private final LivenessSessionRepository livenessSessionRepository;
    private final JwtService jwtService;
    private final LivenessService livenessService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public IssuedChallenge issue(User user) {
        byte[] random = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(random);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(random);

        FaceLoginChallenge challenge = FaceLoginChallenge.builder()
                .user(user)
                .tokenHash(sha256(rawToken))
                .authVersion(user.getAuthVersion())
                .expiresAt(LocalDateTime.now().plusMinutes(CHALLENGE_MINUTES))
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(challenge);

        return new IssuedChallenge(rawToken, challenge.getExpiresAt());
    }

    @Transactional(readOnly = true)
    public FaceLoginChallenge requireActive(String rawToken) {
        return validateActive(findByRawToken(rawToken, false));
    }

    /**
     * Creates at most one LOGIN liveness session for a face challenge. The
     * challenge row is locked before session creation, so concurrent starts on
     * different API instances serialize and either create-and-bind once or
     * return the session that is already bound.
     */
    @Transactional
    public LivenessSessionResponse startOrGetLivenessSession(String rawToken) {
        FaceLoginChallenge challenge = validateActive(findByRawToken(rawToken, true));

        UUID existingSessionId = challenge.getLivenessSessionId();
        if (existingSessionId != null) {
            return livenessService.status(challenge.getUser(), existingSessionId);
        }

        LivenessSessionResponse response = livenessService.start(challenge.getUser(), "LOGIN");
        challenge.setLivenessSessionId(response.sessionId());
        repository.save(challenge);
        return response;
    }

    @Transactional
    public FaceLoginChallenge bindLivenessSession(String rawToken, UUID livenessSessionId) {
        if (livenessSessionId == null) {
            throw new IllegalArgumentException("Liveness session is required");
        }

        FaceLoginChallenge challenge = validateActive(findByRawToken(rawToken, true));

        UUID existingSessionId = challenge.getLivenessSessionId();
        if (existingSessionId != null) {
            if (existingSessionId.equals(livenessSessionId)) {
                return challenge;
            }
            throw new IllegalStateException("Face verification challenge is already bound to another session");
        }

        challenge.setLivenessSessionId(livenessSessionId);
        return repository.save(challenge);
    }

    @Transactional
    public String complete(String rawToken, UUID livenessSessionId) {
        if (livenessSessionId == null) {
            throw new IllegalArgumentException("Liveness session is required");
        }

        /*
         * The pessimistic write lock is the one-time-session gate. A second
         * concurrent completion waits for the first transaction, then observes
         * consumedAt and fails before another JWT can be issued.
         */
        FaceLoginChallenge challenge = validateActive(findByRawToken(rawToken, true));

        if (challenge.getLivenessSessionId() == null ||
                !challenge.getLivenessSessionId().equals(livenessSessionId)) {
            throw new IllegalArgumentException("Liveness session does not belong to this login challenge");
        }

        LivenessSession session = livenessSessionRepository.findById(livenessSessionId)
                .orElseThrow(() -> new IllegalArgumentException("Liveness session not found"));

        if (!session.getUser().getId().equals(challenge.getUser().getId())) {
            throw new IllegalArgumentException("Liveness session does not belong to this login challenge");
        }
        if (!"LOGIN".equalsIgnoreCase(session.getPurpose())) {
            throw new IllegalArgumentException("Liveness session is not a login verification session");
        }
        if (session.getStatus() != LivenessStatus.VERIFIED || session.getVerifiedAt() == null) {
            throw new IllegalStateException("Face verification is not complete");
        }

        User user = challenge.getUser();
        if (!user.isEnabled()) {
            throw new IllegalStateException("Account is not active");
        }

        /*
         * Re-check the authentication epoch immediately before consuming the
         * challenge and minting a JWT. This prevents a credential reset that
         * happened after challenge issuance from being bypassed by finishing an
         * older liveness session.
         */
        if (challenge.getAuthVersion() != user.getAuthVersion()) {
            throw new IllegalStateException(
                    "Face verification challenge is no longer valid. Start login again."
            );
        }

        challenge.setConsumedAt(LocalDateTime.now());
        repository.save(challenge);

        return jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getAuthVersion()
        );
    }

    private FaceLoginChallenge findByRawToken(String rawToken, boolean forUpdate) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Face verification challenge is required");
        }

        String tokenHash = sha256(rawToken.trim());
        return (forUpdate
                ? repository.findByTokenHashForUpdate(tokenHash)
                : repository.findByTokenHash(tokenHash))
                .orElseThrow(() -> new IllegalArgumentException("Invalid face verification challenge"));
    }

    private FaceLoginChallenge validateActive(FaceLoginChallenge challenge) {
        if (challenge.getConsumedAt() != null) {
            throw new IllegalStateException("Face verification challenge was already used");
        }
        if (challenge.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Face verification challenge expired. Start login again.");
        }
        if (challenge.getUser() == null
                || challenge.getAuthVersion() != challenge.getUser().getAuthVersion()) {
            throw new IllegalStateException(
                    "Face verification challenge is no longer valid. Start login again."
            );
        }
        return challenge;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    public record IssuedChallenge(String token, LocalDateTime expiresAt) {}
}
