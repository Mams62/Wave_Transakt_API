package com.wavetransakt.card.controller;

import com.wavetransakt.card.service.WaveCardService;
import com.wavetransakt.user.entity.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cards")
public class WaveCardController {

    private final WaveCardService cardService;

    public WaveCardController(WaveCardService cardService) {
        this.cardService = cardService;
    }

    @GetMapping("/readiness")
    public ResponseEntity<List<WaveCardService.ProviderReadinessView>> readiness(
            Authentication authentication
    ) {
        authenticatedUser(authentication);
        return ResponseEntity.ok(cardService.readiness());
    }

    @GetMapping
    public ResponseEntity<List<WaveCardService.CardView>> list(
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.list(authenticatedUser(authentication)));
    }

    @PostMapping
    public ResponseEntity<WaveCardService.CardView> requestCard(
            @RequestBody(required = false) CardRequest request,
            Authentication authentication
    ) {
        String provider = request == null ? null : request.provider();
        String productCode = request == null ? null : request.productCode();
        return ResponseEntity.ok(
                cardService.requestCard(authenticatedUser(authentication), provider, productCode)
        );
    }

    @PostMapping("/{cardId}/activate")
    public ResponseEntity<WaveCardService.CardView> activate(
            @PathVariable UUID cardId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.activate(authenticatedUser(authentication), cardId));
    }

    @PostMapping("/{cardId}/block")
    public ResponseEntity<WaveCardService.CardView> block(
            @PathVariable UUID cardId,
            @RequestBody(required = false) CardReasonRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.block(
                authenticatedUser(authentication),
                cardId,
                request == null ? null : request.reason()
        ));
    }

    @PostMapping("/{cardId}/unblock")
    public ResponseEntity<WaveCardService.CardView> unblock(
            @PathVariable UUID cardId,
            @RequestBody(required = false) CardReasonRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.unblock(
                authenticatedUser(authentication),
                cardId,
                request == null ? null : request.reason()
        ));
    }

    @PostMapping("/{cardId}/refresh")
    public ResponseEntity<WaveCardService.CardView> refresh(
            @PathVariable UUID cardId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(cardService.refresh(authenticatedUser(authentication), cardId));
    }

    private User authenticatedUser(Authentication authentication) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        return user;
    }

    public record CardRequest(String provider, String productCode) {}

    public record CardReasonRequest(String reason) {}
}
