package com.wavetransakt.payment.controller;

import com.wavetransakt.payment.service.PaystackWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Legacy Paystack webhook. Disabled by default during the Wema migration. */
@RestController
@RequestMapping("/api/v1/payments/webhook")
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "wave.legacy",
        name = "paystack-enabled",
        havingValue = "true"
)
public class PaystackWebhookController {

    private final PaystackWebhookService paystackWebhookService;

    @PostMapping("/paystack")
    public ResponseEntity<Void> paystackWebhook(
            @RequestHeader(
                    value = "x-paystack-signature",
                    required = false
            )
            String signature,
            @RequestBody String rawBody
    ) {
        paystackWebhookService.processWebhook(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
