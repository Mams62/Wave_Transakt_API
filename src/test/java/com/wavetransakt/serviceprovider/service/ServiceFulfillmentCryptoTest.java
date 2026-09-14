package com.wavetransakt.serviceprovider.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceFulfillmentCryptoTest {

    @Test
    void encryptsAndDecryptsProviderFulfillment() {
        byte[] key = "0123456789abcdef0123456789abcdef"
                .getBytes(StandardCharsets.UTF_8);
        ServiceFulfillmentCrypto crypto = new ServiceFulfillmentCrypto(
                Base64.getEncoder().encodeToString(key)
        );

        String plaintext = "Serial No:WRN123456790, pin: 098765432112";
        String encrypted = crypto.encrypt(plaintext);

        assertTrue(encrypted.startsWith("v1:"));
        assertNotEquals(plaintext, encrypted);
        assertEquals(plaintext, crypto.decrypt(encrypted));
    }

    @Test
    void missingEncryptionKeyFailsClosed() {
        ServiceFulfillmentCrypto crypto = new ServiceFulfillmentCrypto("");

        assertThrows(
                IllegalStateException.class,
                () -> crypto.encrypt("provider-issued-pin")
        );
    }
}
