package com.wavetransakt.verification.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class EmailOtpSenderTest {
    @Test
    void refusesDeliveryWithoutProviderCredentials() {
        EmailOtpSender sender = new EmailOtpSender();
        assertFalse(sender.isConfigured());
        assertThrows(IllegalStateException.class,
                () -> sender.sendVerificationCode("example@example.org", "123456"));
    }

    @Test
    void requiresTlsAndNeverAcceptsInvalidVerificationCodes() {
        EmailOtpSender sender = new EmailOtpSender();
        ReflectionTestUtils.setField(sender, "host", "smtp.example.org");
        ReflectionTestUtils.setField(sender, "port", 587);
        ReflectionTestUtils.setField(sender, "username", "mailer");
        ReflectionTestUtils.setField(sender, "password", "placeholder-test-value");
        ReflectionTestUtils.setField(sender, "from", "verify@example.org");
        ReflectionTestUtils.setField(sender, "startTls", false);
        assertFalse(sender.isConfigured());

        ReflectionTestUtils.setField(sender, "startTls", true);
        assertTrue(sender.isConfigured());
        // These fail before any network access to the placeholder SMTP host.
        assertThrows(IllegalArgumentException.class,
                () -> sender.sendVerificationCode("example@example.org", "not-a-code"));
        assertThrows(IllegalArgumentException.class,
                () -> sender.sendVerificationCode("bad-recipient", "123456"));
    }
}
