package com.wavetransakt.kyc;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PremblyKycProviderTest {
    private final PremblyKycProvider provider = new PremblyKycProvider();

    @Test void unavailableUntilConfigured() {
        var request = new IdentityVerificationRequest("TEST_ONLY", "TEST_ONLY",
                "Test", "Person", LocalDate.of(2000, 1, 1), true, "test");
        assertEquals(VerificationDecision.UNAVAILABLE, provider.verify(request));
    }

    @Test void missingConsentIsRejected() {
        var request = new IdentityVerificationRequest("TEST_ONLY", "TEST_ONLY",
                "Test", "Person", LocalDate.of(2000, 1, 1), false, "test");
        assertEquals(VerificationDecision.REJECTED, provider.verify(request));
    }
}
