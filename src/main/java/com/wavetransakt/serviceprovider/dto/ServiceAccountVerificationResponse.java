package com.wavetransakt.serviceprovider.dto;

public record ServiceAccountVerificationResponse(
        boolean verified,
        String serviceId,
        String billersCode,
        String customerName,
        String address,
        String accountType,
        String message
) {
}
