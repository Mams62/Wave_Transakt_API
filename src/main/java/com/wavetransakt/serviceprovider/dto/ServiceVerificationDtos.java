package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public final class ServiceVerificationDtos {
    private ServiceVerificationDtos() {}

    public record VerifyRequest(
            @NotBlank(message = "Service kind is required")
            @Pattern(regexp = "(?i)ELECTRICITY|TV", message = "Service kind must be ELECTRICITY or TV")
            String serviceKind,
            @NotBlank(message = "Service ID is required")
            @Pattern(regexp = "[A-Za-z0-9_-]{1,80}", message = "Invalid service ID")
            String serviceId,
            @NotBlank(message = "Customer reference is required")
            @Pattern(regexp = "[A-Za-z0-9_-]{5,40}", message = "Invalid customer reference")
            String customerReference,
            String option
    ) {}

    public record VerifyResponse(
            String provider,
            String serviceKind,
            String serviceId,
            String customerReference,
            String customerName,
            String address,
            String accountType,
            String currentPackage,
            BigDecimal renewalAmount,
            BigDecimal minimumAmount,
            boolean valid,
            String message
    ) {}
}
