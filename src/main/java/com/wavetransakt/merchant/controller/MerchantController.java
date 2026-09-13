package com.wavetransakt.merchant.controller;

import com.wavetransakt.merchant.dto.MerchantDtos;
import com.wavetransakt.merchant.service.MerchantService;
import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business")
@RequiredArgsConstructor
public class MerchantController {

    private final MerchantService merchantService;

    @PostMapping("/merchants")
    public ResponseEntity<MerchantDtos.MerchantResponse> createMerchant(
            Authentication authentication,
            @Valid @RequestBody MerchantDtos.CreateMerchantRequest request
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.createMerchant(user.getId(), request));
    }

    @GetMapping("/merchants")
    public ResponseEntity<List<MerchantDtos.MerchantResponse>> getMerchants(
            Authentication authentication
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.getOwnedMerchants(user.getId()));
    }

    @GetMapping("/merchants/{merchantId}/qr")
    public ResponseEntity<MerchantDtos.MerchantQrResponse> getMerchantQr(
            Authentication authentication,
            @PathVariable UUID merchantId
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.getMerchantQr(user.getId(), merchantId));
    }

    /**
     * Resolve a raw scanner payload such as WTW:MERCHANT:MQ-... .
     * This endpoint resolves identity only and never creates, authorizes or
     * settles a payment.
     */
    @PostMapping("/qr/resolve")
    public ResponseEntity<MerchantDtos.MerchantQrResolveResponse> resolveMerchantQrPayload(
            Authentication authentication,
            @Valid @RequestBody MerchantDtos.ResolveMerchantQrRequest request
    ) {
        authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.resolveMerchantQr(request.payload()));
    }

    /**
     * Convenience resolver when the client has already extracted the public ID.
     */
    @GetMapping("/qr/{publicId}")
    public ResponseEntity<MerchantDtos.MerchantQrResolveResponse> resolveMerchantQr(
            Authentication authentication,
            @PathVariable String publicId
    ) {
        authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.resolveMerchantQr(publicId));
    }

    @PostMapping("/merchants/{merchantId}/pos-terminals")
    public ResponseEntity<MerchantDtos.PosTerminalResponse> registerTerminal(
            Authentication authentication,
            @PathVariable UUID merchantId
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(merchantService.registerTerminal(user.getId(), merchantId));
    }

    @GetMapping("/merchants/{merchantId}/pos-terminals")
    public ResponseEntity<List<MerchantDtos.PosTerminalResponse>> getTerminals(
            Authentication authentication,
            @PathVariable UUID merchantId
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.getTerminals(user.getId(), merchantId));
    }

    @GetMapping("/merchants/{merchantId}/payments")
    public ResponseEntity<List<MerchantDtos.MerchantPaymentResponse>> getPayments(
            Authentication authentication,
            @PathVariable UUID merchantId
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(merchantService.getPayments(user.getId(), merchantId));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
