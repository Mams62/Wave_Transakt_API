package com.wavetransakt.partner.sandbox;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * INTERNAL SIMULATION protocol. This is NOT Wema's (as yet unknown) webhook
 * signature algorithm. Never reuse it as a Wema production verifier.
 */
public final class SandboxWebhookSignature {
    private SandboxWebhookSignature() {}

    public static void verify(String secret, String timestamp, String signatureHex, byte[] body, Instant now) {
        if (secret == null || secret.length() < 32 || body == null || body.length == 0 || body.length > 32768) reject();
        if (timestamp == null || !timestamp.matches("[0-9]{10,13}")) reject();
        long sent;
        try { sent = Long.parseLong(timestamp); }
        catch (NumberFormatException ex) { reject(); return; }
        // The simulator signs UNIX seconds and permits at most five minutes'
        // delivery lag and a 30-second sender clock lead.
        if (timestamp.length() != 10 || sent < now.getEpochSecond() - 300 || sent > now.getEpochSecond() + 30) reject();
        if (signatureHex == null || !signatureHex.matches("[0-9a-fA-F]{64}")) reject();
        byte[] supplied = HexFormat.of().parseHex(signatureHex);
        byte[] expected = sign(secret, timestamp, body);
        if (!MessageDigest.isEqual(supplied, expected)) reject();
    }

    public static byte[] sign(String secret, String timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(timestamp.getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            return mac.doFinal(body);
        } catch (Exception ex) {
            throw new IllegalStateException("Sandbox verifier is unavailable", ex);
        }
    }

    public static String sha256Hex(byte[] body) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private static void reject() {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid simulation webhook signature or timestamp");
    }
}
