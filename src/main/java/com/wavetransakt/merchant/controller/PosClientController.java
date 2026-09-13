package com.wavetransakt.merchant.controller;

import com.wavetransakt.merchant.dto.PosDtos;
import com.wavetransakt.merchant.service.PosClientService;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/business/pos")
@RequiredArgsConstructor
public class PosClientController {

    private final PosClientService posClientService;

    @GetMapping("/terminals/{terminalCode}/profile")
    public ResponseEntity<PosDtos.PosProfileResponse> profile(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(posClientService.getProfile(user.getId(), terminalCode));
    }

    @GetMapping("/terminals/{terminalCode}/qr")
    public ResponseEntity<PosDtos.PosQrResponse> qr(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(posClientService.getQr(user.getId(), terminalCode));
    }

    @GetMapping("/terminals/{terminalCode}/transactions")
    public ResponseEntity<List<PosDtos.PosTransactionResponse>> transactions(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(posClientService.getTransactions(user.getId(), terminalCode));
    }

    @GetMapping("/terminals/{terminalCode}/transactions/{paymentId}/receipt")
    public ResponseEntity<PosDtos.PosReceiptResponse> receipt(
            Authentication authentication,
            @PathVariable String terminalCode,
            @PathVariable UUID paymentId
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(posClientService.getReceipt(user.getId(), terminalCode, paymentId));
    }

    @GetMapping("/terminals/{terminalCode}/settlements")
    public ResponseEntity<List<PosDtos.PosSettlementResponse>> settlements(
            Authentication authentication,
            @PathVariable String terminalCode
    ) {
        User user = authenticatedUser(authentication);
        return ResponseEntity.ok(posClientService.getSettlements(user.getId(), terminalCode));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }
}
