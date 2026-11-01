package com.wavetransakt.waveid;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WaveIdServiceTest {
    @Test
    void newIdsAreFourLettersFollowedByFourDigitsAndAreNotSequential() {
        String first = WaveIdService.newCandidate();
        assertTrue(first.matches("[A-Z]{4}[0-9]{4}"));
        boolean foundDifferent = false;
        for (int i = 0; i < 30; i++) {
            String candidate = WaveIdService.newCandidate();
            assertTrue(candidate.matches("[A-Z]{4}[0-9]{4}"));
            if (!candidate.equals(first)) foundDifferent = true;
        }
        assertTrue(foundDifferent);
    }
}
