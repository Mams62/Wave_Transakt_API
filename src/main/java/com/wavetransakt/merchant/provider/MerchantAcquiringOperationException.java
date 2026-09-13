package com.wavetransakt.merchant.provider;

/**
 * Safe exception for provider acquiring operations.
 *
 * Messages must never contain secrets, access tokens, PINs, PAN/card data,
 * settlement account details, NIN/BVN values or raw provider payloads.
 */
public class MerchantAcquiringOperationException extends RuntimeException {

    public MerchantAcquiringOperationException(String message) {
        super(message);
    }

    public MerchantAcquiringOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}
