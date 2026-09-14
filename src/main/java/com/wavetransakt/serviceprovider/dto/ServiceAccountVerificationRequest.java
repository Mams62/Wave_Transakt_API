package com.wavetransakt.serviceprovider.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ServiceAccountVerificationRequest(
        @NotBlank(message = "Service ID is required")
        @Pattern(
                regexp = "[A-Za-z0-9_-]{1,80}",
                message = "Invalid service ID"
        )
        String serviceId,

        @NotBlank(message = "Customer account identifier is required")
        @Pattern(
                regexp = "[A-Za-z0-9._-]{4,40}",
                message = "Invalid customer account identifier"
        )
        String billersCode,

        @Pattern(
                regexp = "[A-Za-z0-9_-]{0,40}",
                message = "Invalid service variation/type"
        )
        String type
) {
}
