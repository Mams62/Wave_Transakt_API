package com.wavetransakt.kyc;

/**
 * Provider-neutral KYC boundary. Do not pass identity numbers to logs.
 * Implementations must never turn a provider timeout into VERIFIED.
 */
public interface IdentityVerificationProvider {
    VerificationDecision verify(IdentityVerificationRequest request);
}
