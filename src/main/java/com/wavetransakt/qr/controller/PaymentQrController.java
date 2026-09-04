package com.wavetransakt.qr.controller;

import com.wavetransakt.qr.dto.CreatePaymentQrRequest;
import com.wavetransakt.qr.dto.PaymentQrPayloadRequest;
import com.wavetransakt.qr.dto.PaymentQrResponse;
import com.wavetransakt.qr.service.PaymentQrService;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.user.entity.User;
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

    /**
     * Recipient creates a one-time payment QR.
     */
    @PostMapping("/create")
    public ResponseEntity<PaymentQrResponse>
    create(
            Authentication authentication,
            @Valid
            @RequestBody
            CreatePaymentQrRequest request
    ) {

        User user =
                requireUser(authentication);

        return ResponseEntity.ok(
                paymentQrService.create(
                        user.getId(),
                        request.getAmount(),
                        request.getDescription()
                )
        );
    }

    /**
     * Payer resolves the scanned QR before confirming.
     *
     * No money moves here.
     */
    @PostMapping("/resolve")
    public ResponseEntity<PaymentQrResponse>
    resolve(
            Authentication authentication,
            @Valid
            @RequestBody
            PaymentQrPayloadRequest request
    ) {

        requireUser(authentication);

        return ResponseEntity.ok(
                paymentQrService.resolve(
                        request.getPayload()
                )
        );
    }

    /**
     * Consume the payment intent.
     */
    @PostMapping("/pay")
    public ResponseEntity<TransactionResponse>
    pay(
            Authentication authentication,

            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            PaymentQrPayloadRequest request
    ) {

        User user =
                requireUser(authentication);

        return ResponseEntity.ok(
                paymentQrService.pay(
                        user.getId(),
                        idempotencyKey,
                        request.getPayload()
                )
        );
    }

    private User requireUser(
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new IllegalArgumentException(
                    "Authentication required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            throw new IllegalArgumentException(
                    "Invalid authentication principal"
            );
        }

        return user;
    }
}