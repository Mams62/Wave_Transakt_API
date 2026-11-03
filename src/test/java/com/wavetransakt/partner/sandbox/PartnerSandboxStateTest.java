package com.wavetransakt.partner.sandbox;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartnerSandboxStateTest {
    @Test void canProgressOnlyThroughRecognizedSimulationStates() {
        assertEquals(PartnerSandboxState.PENDING, PartnerSandboxState.CREATED.next("PENDING"));
        assertEquals(PartnerSandboxState.TEST_CONFIRMED, PartnerSandboxState.PENDING.next("CONFIRMED"));
        assertEquals(PartnerSandboxState.TEST_FAILED, PartnerSandboxState.CREATED.next("FAILED"));
        assertEquals(PartnerSandboxState.TEST_CONFIRMED, PartnerSandboxState.TEST_CONFIRMED.next("CONFIRMED"));
        assertThrows(IllegalArgumentException.class, () -> PartnerSandboxState.CREATED.next("SUCCESS"));
        assertThrows(IllegalArgumentException.class, () -> PartnerSandboxState.CREATED.next("UNKNOWN"));
        assertThrows(IllegalArgumentException.class, () -> PartnerSandboxState.CREATED.next(""));
    }
    @Test void contradictoryTerminalOutcomesRequireReview() {
        assertEquals(PartnerSandboxState.REVIEW_REQUIRED, PartnerSandboxState.TEST_CONFIRMED.next("FAILED"));
        assertEquals(PartnerSandboxState.REVIEW_REQUIRED, PartnerSandboxState.TEST_FAILED.next("CONFIRMED"));
        assertEquals(PartnerSandboxState.REVIEW_REQUIRED, PartnerSandboxState.REVIEW_REQUIRED.next("CONFIRMED"));
    }
}
