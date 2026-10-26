package com.wavetransakt.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VerificationCodeExposureGuardTest {

    @Test
    void acceptsDisabledDemoCodeExposure() {
        assertDoesNotThrow(() -> new VerificationCodeExposureGuard(false));
    }

    @Test
    void refusesStartupWhenOtpExposureIsEnabled() {
        assertThrows(IllegalStateException.class,
                () -> new VerificationCodeExposureGuard(true));
    }
}
