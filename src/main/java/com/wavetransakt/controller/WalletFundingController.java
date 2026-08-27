package com.wavetransakt.controller;

import com.wavetransakt.dto.InitializePaymentRequest;
import com.wavetransakt.dto.PaymentResponse;
import com.wavetransakt.service.WalletFundingService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallet/funding")
public class WalletFundingController {
    private final WalletFundingService fundingService;

    public WalletFundingController(WalletFundingService fundingService) {
        this.fundingService = fundingService;
    }

    // Temporary user header until Wave Transakt JWT authentication is wired in.
    @PostMapping("/initialize")
    public ResponseEntity<PaymentResponse> initialize(
            @RequestHeader("X-User-Id") UUID userId,
            @Valid @RequestBody InitializePaymentRequest request) {
        return ResponseEntity.ok(fundingService.initialize(userId, request));
    }

    @GetMapping("/verify/{reference}")
    public ResponseEntity<PaymentResponse> verify(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable String reference) {
        return ResponseEntity.ok(fundingService.verify(userId, reference));
    }
}
