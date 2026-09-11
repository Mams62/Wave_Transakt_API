package com.wavetransakt.qr.controller;

import com.wavetransakt.qr.dto.CreatePaymentQrRequest;
import com.wavetransakt.qr.dto.PaymentQrPayloadRequest;
import com.wavetransakt.qr.dto.PaymentQrResponse;
import com.wavetransakt.qr.service.PaymentQrService;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.service.WemaSettlementGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qr/payment")
@RequiredArgsConstructor
public class PaymentQrController {

    private final PaymentQrService paymentQrService;
    private final WemaSettlementGuard settlementGuard;

    /** Recipient can still create a one-time intent during controlled testing. */
    @PostMapping("/create")
    public ResponseEntity<PaymentQrResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreatePaymentQrRequest request
    ) {
        User user = requireUser(authentication);
        return ResponseEntity.ok(
                paymentQrService.create(
                        user.getId(),
                        request.getAmount(),
                        request.getDescription()
                )
        );
    }

    /** Resolve only; no money moves. */
    @PostMapping("/resolve")
    public ResponseEntity<PaymentQrResponse> resolve(
            Authentication authentication,
            @Valid @RequestBody PaymentQrPayloadRequest request
    ) {
        requireUser(authentication);
        return ResponseEntity.ok(
                paymentQrService.resolve(request.getPayload())
        );
    }

    /**
     * The legacy local ledger debit is paused until the same instruction is
     * settled against the Wema wallet source of funds.
     */
    @PostMapping("/pay")
    public ResponseEntity<TransactionResponse> pay(
            Authentication authentication,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,
            @Valid @RequestBody PaymentQrPayloadRequest request
    ) {
        settlementGuard.requireMoneyMovementEnabled();
        User user = requireUser(authentication);
        return ResponseEntity.ok(
                paymentQrService.pay(
                        user.getId(),
                        idempotencyKey,
                        request.getPayload()
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
