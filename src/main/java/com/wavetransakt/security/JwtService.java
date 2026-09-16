package com.wavetransakt.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    public static final String ACCESS_FULL = "FULL";
    public static final String ACCESS_SETUP_ONLY = "SETUP_ONLY";
    private static final String AUTH_VERSION_CLAIM = "ver";

    private final SecretKey secretKey;
    private final long expiration;
    private final long setupExpiration;

    public JwtService(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiration:86400000}") long expiration,
            @Value("${jwt.setup-expiration:1800000}") long setupExpiration
    ) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(
                    "jwt.secret must be at least 32 characters long"
            );
        }

        this.secretKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expiration = expiration;
        this.setupExpiration = setupExpiration;
    }

    public String generateToken(UUID userId, String email, long authVersion) {
        return generateToken(userId, email, authVersion, ACCESS_FULL, expiration);
    }

    public String generateSetupToken(UUID userId, String email, long authVersion) {
        return generateToken(userId, email, authVersion, ACCESS_SETUP_ONLY, setupExpiration);
    }

    private String generateToken(
            UUID userId,
            String email,
            long authVersion,
            String access,
            long ttlMillis
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("JWT user ID is required");
        }
        if (authVersion < 0) {
            throw new IllegalArgumentException("JWT authentication version must not be negative");
        }

        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttlMillis);

        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("access", access)
                .claim(AUTH_VERSION_CLAIM, authVersion)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String extractUserId(String token) {
        return claims(token).getSubject();
    }

    public String extractEmail(String token) {
        return claims(token).get("email", String.class);
    }

    public String extractAccess(String token) {
        String access = claims(token).get("access", String.class);
        // Backward compatibility for already-issued normal JWTs that pre-date the claim.
        return access == null || access.isBlank() ? ACCESS_FULL : access;
    }

    /**
     * Tokens issued before V118 have no authentication-version claim. Treat
     * them as version 0 so deployment itself does not force-log-out every user.
     * The next credential reset advances the user and invalidates those tokens.
     */
    public long extractAuthVersion(String token) {
        Object raw = claims(token).get(AUTH_VERSION_CLAIM);
        if (raw == null) {
            return 0L;
        }
        if (raw instanceof Number number) {
            long value = number.longValue();
            if (value < 0) {
                throw new IllegalArgumentException("Invalid JWT authentication version");
            }
            return value;
        }
        try {
            long value = Long.parseLong(raw.toString());
            if (value < 0) {
                throw new IllegalArgumentException("Invalid JWT authentication version");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid JWT authentication version", exception);
        }
    }

    public boolean isTokenValid(String token) {
        try {
            claims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
