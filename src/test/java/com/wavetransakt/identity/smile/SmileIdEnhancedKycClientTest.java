package com.wavetransakt.identity.smile;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SmileIdEnhancedKycClientTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID userId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();

    private SmileIdEnhancedKycClient.VerificationInput input(boolean consent, String idType, String idNumber) {
        return new SmileIdEnhancedKycClient.VerificationInput(
                userId, attemptId, idType, idNumber, "Test", "Person",
                "sandbox@example.com", "+2348012345678", consent, Instant.now());
    }

    @Test
    void disabledByDefaultEvenWithCredentials() {
        var client = new SmileIdEnhancedKycClient(mapper, false, "1234",
                "NOT_A_REAL_KEY", "https://example.com/webhooks/smile",
                "https://example.com/privacy");
        assertFalse(client.isConfiguredForSandbox());
        assertThrows(IllegalStateException.class,
                () -> client.submitEnhancedKyc(input(true, "BVN", "12345678901")));
    }

    @Test
    void mustHaveConsentBeforeAnyNetworkRequest() {
        var input = input(false, "NIN", "12345678901");
        assertThrows(IllegalArgumentException.class, () -> SmileIdEnhancedKycClient.validate(input));
    }

    @Test
    void rejectsInvalidIdentifierAndType() {
        assertThrows(IllegalArgumentException.class,
                () -> SmileIdEnhancedKycClient.validate(input(true, "PASSPORT", "12345678901")));
        assertThrows(IllegalArgumentException.class,
                () -> SmileIdEnhancedKycClient.validate(input(true, "BVN", "111")));
    }

    @Test
    void rejectsFutureConsentAndMissingAttempt() {
        var input = new SmileIdEnhancedKycClient.VerificationInput(
                userId, null, "BVN", "12345678901", "Test", "Person",
                "sandbox@example.com", null, true, Instant.now().plusSeconds(3600));
        assertThrows(IllegalArgumentException.class, () -> SmileIdEnhancedKycClient.validate(input));
    }

    @Test
    void acceptedJobIsAlwaysPendingNotVerified() throws Exception {
        String json = "{\"status\":\"Accepted\",\"job_id\":\"sandbox-job\",\"user_id\":\"" + userId + "\"}";
        var job = SmileIdEnhancedKycClient.parseAccepted(mapper.readTree(json), userId.toString());
        assertEquals("PENDING", job.status());
        assertEquals("sandbox-job", job.jobId());
    }

    @Test
    void rejectsMismatchedUserOrNonAcceptedStatus() throws Exception {
        var wrong = mapper.readTree("{\"status\":\"Accepted\",\"job_id\":\"job\",\"user_id\":\"other\"}");
        assertThrows(IllegalStateException.class,
                () -> SmileIdEnhancedKycClient.parseAccepted(wrong, userId.toString()));
        var rejected = mapper.readTree("{\"status\":\"Rejected\",\"job_id\":\"job\",\"user_id\":\"" + userId + "\"}");
        assertThrows(IllegalStateException.class,
                () -> SmileIdEnhancedKycClient.parseAccepted(rejected, userId.toString()));
    }

    @Test
    void rejectsInsecureCallbackConfiguration() {
        var client = new SmileIdEnhancedKycClient(mapper, true, "1234",
                "TEST_KEY_ONLY", "http://localhost/webhook", "https://example.com/privacy");
        assertFalse(client.isConfiguredForSandbox());
    }
}
