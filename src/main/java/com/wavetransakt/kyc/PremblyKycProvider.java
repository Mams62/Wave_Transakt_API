package com.wavetransakt.kyc;

import org.springframework.stereotype.Component;

/**
 * Safe initial adapter: intentionally fails closed.
 * Replace with approved, documented Prembly BVN + NIN calls and biometric
 * verification only after sandbox credentials and API contract confirmation.
 */
@Component
public final class PremblyKycProvider implements IdentityVerificationProvider {
    @Override
    public VerificationDecision verify(IdentityVerificationRequest request) {
        if (request == null || !request.consentGranted()) {
            return VerificationDecision.REJECTED;
        }
        return VerificationDecision.UNAVAILABLE;
    }
}
