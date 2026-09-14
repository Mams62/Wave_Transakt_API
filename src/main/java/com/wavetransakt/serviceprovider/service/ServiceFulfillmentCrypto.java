package com.wavetransakt.serviceprovider.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Service
public class ServiceFulfillmentCrypto {

    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final String encodedKey;

    public ServiceFulfillmentCrypto(
            @Value("${WAVE_SERVICE_FULFILLMENT_KEY_BASE64:}") String encodedKey
    ) {
        this.encodedKey = encodedKey == null ? "" : encodedKey.trim();
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }

        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            byte[] ciphertext = cipher.doFinal(
                    plaintext.trim().getBytes(StandardCharsets.UTF_8)
            );
            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);

            return PREFIX + Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to protect service fulfillment", ex);
        }
    }

    public String decrypt(String protectedValue) {
        if (protectedValue == null || protectedValue.isBlank()) {
            return null;
        }
        if (!protectedValue.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported service fulfillment format");
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(
                    protectedValue.substring(PREFIX.length())
            );
            if (payload.length <= IV_BYTES) {
                throw new IllegalStateException("Invalid protected service fulfillment");
            }

            byte[] iv = Arrays.copyOfRange(payload, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key(),
                    new GCMParameterSpec(GCM_TAG_BITS, iv)
            );

            return new String(
                    cipher.doFinal(ciphertext),
                    StandardCharsets.UTF_8
            );
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to read service fulfillment", ex);
        }
    }

    private SecretKey key() {
        if (encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "Service fulfillment encryption key is not configured"
            );
        }

        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Service fulfillment encryption key is invalid",
                    ex
            );
        }

        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(
                    "Service fulfillment encryption key must decode to 32 bytes"
            );
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
