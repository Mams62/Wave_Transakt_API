package com.wavetransakt.partner.sandbox;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class SandboxWebhookSignatureTest {
    private static final String SECRET = "local-test-secret-only-not-for-production-1234567";
    private static final byte[] BODY = "{\"reference\":\"SIM-test\"}".getBytes(StandardCharsets.UTF_8);

    @Test void acceptsCorrectTimestampBoundToExactBytes() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String ts = Long.toString(now.getEpochSecond());
        String mac = HexFormat.of().formatHex(SandboxWebhookSignature.sign(SECRET, ts, BODY));
        assertDoesNotThrow(() -> SandboxWebhookSignature.verify(SECRET, ts, mac, BODY, now));
        assertThrows(ResponseStatusException.class, () ->
                SandboxWebhookSignature.verify(SECRET, ts, mac, "{ }".getBytes(StandardCharsets.UTF_8), now));
    }
    @Test void rejectsReplayExpiredAndFutureTimestamps() {
        Instant now = Instant.ofEpochSecond(1_800_000_000L);
        String expired = Long.toString(now.minusSeconds(301).getEpochSecond());
        String tooNew = Long.toString(now.plusSeconds(31).getEpochSecond());
        assertThrows(ResponseStatusException.class, () ->
                SandboxWebhookSignature.verify(SECRET, expired,
                        HexFormat.of().formatHex(SandboxWebhookSignature.sign(SECRET, expired, BODY)), BODY, now));
        assertThrows(ResponseStatusException.class, () ->
                SandboxWebhookSignature.verify(SECRET, tooNew,
                        HexFormat.of().formatHex(SandboxWebhookSignature.sign(SECRET, tooNew, BODY)), BODY, now));
    }
    @Test void rejectsMissingSecretEmptyPayloadAndMalformedSignature() {
        assertThrows(ResponseStatusException.class, () ->
                SandboxWebhookSignature.verify("tiny", "1800000000", "00", BODY, Instant.ofEpochSecond(1_800_000_000L)));
        assertThrows(ResponseStatusException.class, () ->
                SandboxWebhookSignature.verify(SECRET, "1800000000", "xx", BODY, Instant.ofEpochSecond(1_800_000_000L)));
    }
}
