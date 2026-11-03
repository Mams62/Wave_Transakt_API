package com.wavetransakt.partner.sandbox;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Internal test-harness callback ONLY: NOT Wema's webhook contract.
 * Never submit this URL to a bank's production dashboard.
 */
@RestController
public class SandboxWebhookController {
    private final PartnerSandboxService service;

    public SandboxWebhookController(PartnerSandboxService service) { this.service = service; }

    @PostMapping(value = "/api/v1/webhooks/wema/simulation",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PartnerSandboxService.CallbackReceipt> callback(
            @RequestHeader("X-Wave-Sandbox-Event-Id") String eventId,
            @RequestHeader("X-Wave-Sandbox-Timestamp") String timestamp,
            @RequestHeader("X-Wave-Sandbox-Signature") String signature,
            @RequestBody byte[] rawBody
    ) {
        PartnerSandboxService.CallbackReceipt receipt =
                service.receiveSimulationCallback(eventId, timestamp, signature, rawBody);
        return ResponseEntity.status(receipt.duplicate() ? 200 : 202)
                .header("Cache-Control", "no-store").body(receipt);
    }
}
