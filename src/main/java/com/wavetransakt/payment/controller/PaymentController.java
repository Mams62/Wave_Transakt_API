package com.wavetransakt.payment.controller;

import com.wavetransakt.payment.dto.InitializePaymentRequest;
import com.wavetransakt.payment.dto.VerifyPaymentRequest;
import com.wavetransakt.payment.service.PaystackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaystackService paystackService;

    @PostMapping("/initialize")
    public ResponseEntity<String> initializePayment(
            @Valid @RequestBody InitializePaymentRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                paystackService.initializeTransaction(
                        request,
                        authentication
                )
        );
    }

    @PostMapping("/verify")
    public ResponseEntity<String> verifyPayment(
            @Valid @RequestBody VerifyPaymentRequest request,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                paystackService.verifyTransaction(
                        request.getReference(),
                        authentication
                )
        );
    }

    @GetMapping("/verify/{reference}")
    public ResponseEntity<String> verify(
            @PathVariable String reference,
            Authentication authentication
    ) {

        return ResponseEntity.ok(
                paystackService.verifyTransaction(
                        reference,
                        authentication
                )
        );
    }
}