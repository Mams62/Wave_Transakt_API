package com.wavetransakt.serviceprovider.controller;

import com.wavetransakt.serviceprovider.dto.ServiceAccountVerificationRequest;
import com.wavetransakt.serviceprovider.dto.ServiceAccountVerificationResponse;
import com.wavetransakt.serviceprovider.vtpass.VtpassVerificationClient;
import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServiceVerificationController {

    private final VtpassVerificationClient verificationClient;

    @PostMapping("/verify")
    public ResponseEntity<ServiceAccountVerificationResponse> verify(
            Authentication authentication,
            @Valid @RequestBody ServiceAccountVerificationRequest request
    ) {
        authenticatedUser(authentication);
        return ResponseEntity.ok(verificationClient.verify(request));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
