package com.wavetransakt.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record RegisterRequest(
            @NotBlank @Size(max = 60) String firstName,
            @NotBlank @Size(max = 60) String lastName,
            @NotBlank String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            String phone,
            String bvn,
            String nin) {}

    public record LoginRequest(
            @NotBlank String identifier,
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
