package com.wavetransakt.kyc;

import java.time.LocalDate;

/** Server-side only. Do not serialize this object to logs or audit events. */
public record IdentityVerificationRequest(
        String bvn,
        String nin,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        boolean consentGranted,
        String correlationId
) {}
