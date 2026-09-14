package com.wavetransakt.card.provider;

/**
 * Raised when a card-issuing operation cannot be performed safely.
 *
 * Provider secrets, PANs, CVVs, issuer credentials and raw provider payloads
 * must never be included in this exception message.
 */
public class CardIssuingOperationException extends RuntimeException {

    public CardIssuingOperationException(String message) {
        super(message);
    }

    public CardIssuingOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
