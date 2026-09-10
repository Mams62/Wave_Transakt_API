package com.wavetransakt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class AuthRequests {
    private AuthRequests() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @Size(max = 20) String phone,
            @Pattern(regexp = "^$|\\d{11}$", message = "BVN must be 11 digits") String bvn,
            @Pattern(regexp = "^$|\\d{11}$", message = "NIN must be 11 digits") String nin,
            @Size(max = 80) String state,
            @Size(max = 100) String localGovernment,
            @Past LocalDate dateOfBirth,
            @Size(max = 30) String gender,
            @Pattern(regexp = "^$|\\d{6}$", message = "Transaction PIN must be 6 digits") String transactionPin) {}

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
