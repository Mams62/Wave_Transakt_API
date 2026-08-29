package com.wavetransakt.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthRequests {
    private AuthRequests() {}

    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Size(max = 120) String fullName) {}

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 128) String password) {}
}
