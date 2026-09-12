package com.wavetransakt.identity.controller;

import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/identity/liveness")
@RequiredArgsConstructor
public class LivenessController {

    private final LivenessService livenessService;

    @PostMapping("/start")
    public ResponseEntity<LivenessSessionResponse> start(
            Authentication authentication,
            @RequestBody(required = false) StartRequest request
    ) {
        User user = requireUser(authentication);
        String purpose = request == null ? "LOGIN" : request.purpose();
        return ResponseEntity.ok(livenessService.start(user, purpose));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<LivenessSessionResponse> status(
            Authentication authentication,
            @PathVariable UUID sessionId
    ) {
        return ResponseEntity.ok(
                livenessService.status(requireUser(authentication), sessionId)
        );
    }

    @GetMapping("/latest")
    public ResponseEntity<LivenessSessionResponse> latest(
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                livenessService.latest(requireUser(authentication))
        );
    }

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(value = "X-Wave-Liveness-Secret", required = false) String secret,
            @RequestBody ProviderResultRequest request
    ) {
        livenessService.applyProviderResult(
                secret,
                request.sessionId(),
                request.providerSessionId(),
                request.status(),
                request.message()
        );
        return ResponseEntity.noContent().build();
    }

    private User requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authentication required");
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            throw new IllegalArgumentException("Invalid authentication principal");
        }
        return user;
    }

    public record StartRequest(String purpose) {}

    public record ProviderResultRequest(
            UUID sessionId,
            String providerSessionId,
            String status,
            String message
    ) {}
}
