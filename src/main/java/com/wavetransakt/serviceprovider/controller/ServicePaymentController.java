package com.wavetransakt.serviceprovider.controller;

import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.dto.ServicePurchaseRequest;
import com.wavetransakt.serviceprovider.service.ServicePaymentService;
import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/services")
@RequiredArgsConstructor
public class ServicePaymentController {

    private final ServicePaymentService servicePaymentService;

    @PostMapping("/pay")
    public ResponseEntity<ServicePaymentResponse> pay(
            Authentication authentication,
            @RequestHeader(value = "Idempotency-Key", required = false)
            String idempotencyKey,
            @Valid @RequestBody ServicePurchaseRequest request
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                servicePaymentService.purchase(user.getId(), idempotencyKey, request)
        );
    }

    @GetMapping("/payments/{reference}")
    public ResponseEntity<ServicePaymentResponse> payment(
            Authentication authentication,
            @PathVariable String reference
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                servicePaymentService.getPayment(user.getId(), reference)
        );
    }

    @PostMapping("/payments/{reference}/requery")
    public ResponseEntity<ServicePaymentResponse> requery(
            Authentication authentication,
            @PathVariable String reference
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(
                servicePaymentService.requery(user.getId(), reference)
        );
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
