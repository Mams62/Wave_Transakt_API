package com.wavetransakt.partner.sandbox;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only via SecurityConfig /api/v1/admin/** role check. Read-only. */
@RestController
public class PartnerSandboxAdminController {
    private final PartnerSandboxService service;
    public PartnerSandboxAdminController(PartnerSandboxService service) { this.service = service; }

    @GetMapping("/api/v1/admin/wema/sandbox-reconciliation")
    public ResponseEntity<PartnerSandboxService.ReconciliationReport> report() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.reconciliation());
    }
}
