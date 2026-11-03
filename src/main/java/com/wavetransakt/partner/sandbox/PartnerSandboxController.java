package com.wavetransakt.partner.sandbox;

import com.wavetransakt.user.entity.User;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

/** Reuses existing JWT ACCESS_FULL rules. UI may READ readiness, not move funds. */
@RestController
@RequestMapping("/api/v1/partner/sandbox")
public class PartnerSandboxController {
    private final PartnerSandboxService service;

    public PartnerSandboxController(PartnerSandboxService service) { this.service = service; }

    @GetMapping("/readiness")
    public ResponseEntity<PartnerSandboxService.Readiness> readiness() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.readiness());
    }

    @PostMapping("/intents")
    public ResponseEntity<PartnerSandboxService.IntentView> create(
            Authentication auth,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PartnerSandboxService.IntentRequest request
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.create(userId(auth), idempotencyKey, request));
    }

    @GetMapping("/intents/{reference}")
    public ResponseEntity<PartnerSandboxService.IntentView> get(
            Authentication auth, @PathVariable String reference
    ) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.owned(userId(auth), reference));
    }

    private UUID userId(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User user)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.UNAUTHORIZED, "Authenticated account required");
        }
        return user.getId();
    }
}
