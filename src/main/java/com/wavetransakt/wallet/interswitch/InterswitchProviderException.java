package com.wavetransakt.wallet.interswitch;

/**
 * Safe provider-layer exception for Interswitch integration failures.
 *
 * Secrets, tokens, raw provider payloads, and personally identifying values
 * must never be included in this exception message because it may surface
 * through the API error handler during controlled testing.
 */
public class InterswitchProviderException extends RuntimeException {

    public InterswitchProviderException(String message) {
        super(message);
    }

    public InterswitchProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
