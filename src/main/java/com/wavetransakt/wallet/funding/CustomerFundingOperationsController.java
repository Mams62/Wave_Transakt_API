package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/funding-reconciliation")
public class CustomerFundingOperationsController {

    private final CustomerFundingOperationsService operationsService;

    public CustomerFundingOperationsController(CustomerFundingOperationsService operationsService) {
        this.operationsService = operationsService;
    }

    @GetMapping("/events")
    public ResponseEntity<CustomerFundingOperationsService.FundingEventPage> listEvents(
            @RequestParam(required = false) CustomerFundingEventStatus status,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                operationsService.listEvents(status, provider, page, size, actor(authentication))
        );
    }

    @GetMapping("/events/{eventId}")
    public ResponseEntity<CustomerFundingOperationsService.FundingEventDetail> getEvent(
            @PathVariable UUID eventId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(operationsService.getEvent(eventId, actor(authentication)));
    }

    @PostMapping("/events/{eventId}/actions")
    public ResponseEntity<CustomerFundingOperationsService.ReconciliationActionView> recordAction(
            @PathVariable UUID eventId,
            @RequestBody ReconciliationActionRequest request,
            Authentication authentication
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Reconciliation action request is required");
        }
        return ResponseEntity.ok(
                operationsService.recordAction(
                        eventId,
                        request.actionType(),
                        request.reasonCode(),
                        actor(authentication)
                )
        );
    }

    private User actor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof User user)) {
            throw new AccessDeniedException("Administrator authentication is required");
        }
        return user;
    }

    public record ReconciliationActionRequest(
            FundingReconciliationActionType actionType,
            FundingReconciliationReasonCode reasonCode
    ) {
    }
}
