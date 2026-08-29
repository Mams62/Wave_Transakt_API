package com.wavetransakt.controller;

import com.wavetransakt.dto.InitializePaymentRequest;
import com.wavetransakt.dto.PaymentResponse;
import com.wavetransakt.service.WalletFundingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/funding")
public class WalletFundingController {
    private final WalletFundingService fundingService;

    public WalletFundingController(WalletFundingService fundingService) {
        this.fundingService = fundingService;
    }

    @PostMapping("/initialize")
    public ResponseEntity<PaymentResponse> initialize(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody InitializePaymentRequest request) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(fundingService.initialize(userId, request));
    }

    @GetMapping("/verify/{reference}")
    public ResponseEntity<PaymentResponse> verify(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String reference) {
        UUID userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(fundingService.verify(userId, reference));
    }
}
