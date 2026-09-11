package com.wavetransakt.qr.controller;

import com.wavetransakt.qr.dto.QrTransferRequest;
import com.wavetransakt.qr.dto.WalletQrResponse;
import com.wavetransakt.qr.service.WalletQrService;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.service.WemaSettlementGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class WalletQrController {

    private final WalletQrService walletQrService;
    private final TransactionService transactionService;
    private final WemaSettlementGuard settlementGuard;

    /** Permanent QR creation/lookup never moves money and stays enabled. */
    @GetMapping("/wallet")
    public ResponseEntity<?> getMyWalletQr(
            Authentication authentication
    ) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(
                walletQrService.getWalletQr(user.getId())
        );
    }

    /** Recipient resolution stays enabled during Wema settlement integration. */
    @GetMapping("/wallet/{walletNumber}")
    public ResponseEntity<WalletQrResponse> resolveWalletQr(
            @PathVariable String walletNumber
    ) {
        return ResponseEntity.ok(
                walletQrService.getWalletQrByWalletNumber(walletNumber)
        );
    }

    /**
     * Legacy local-balance QR debit is closed by default while Wema is the
     * source-of-funds provider. It may be re-enabled only after Wema settlement
     * is connected and tested end to end.
     */
    @PostMapping("/wallet/pay")
    public ResponseEntity<TransactionResponse> payWalletQr(
            Authentication authentication,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,
            @Valid @RequestBody QrTransferRequest request
    ) {
        settlementGuard.requireMoneyMovementEnabled();
        User user = requireUser(authentication);

        TransferRequest transferRequest = TransferRequest.builder()
                .receiverWalletNumber(request.getWalletNumber())
                .amount(request.getAmount())
                .description(request.getDescription())
                .build();

        return ResponseEntity.ok(
                transactionService.transfer(
                        user.getId(),
                        idempotencyKey,
                        transferRequest
                )
        );
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            throw new IllegalArgumentException(
                    "Invalid authentication principal"
            );
        }
        return user;
    }
}
