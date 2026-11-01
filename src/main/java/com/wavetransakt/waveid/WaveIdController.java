package com.wavetransakt.waveid;

import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** Authenticated read-only recipient lookup; no wallet debit or credit. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/wave-id")
public class WaveIdController {
    private final WaveIdService service;

    @GetMapping("/me")
    public ResponseEntity<WaveIdService.WaveIdRecord> me(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.myId(requireUser(authentication)));
    }

    @PostMapping("/activate")
    public ResponseEntity<WaveIdService.WaveIdRecord> activate(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.activate(requireUser(authentication)));
    }

    @PostMapping("/resolve")
    public ResponseEntity<WaveIdService.WaveIdRecord> resolve(
            Authentication authentication,
            @Valid @RequestBody ResolveWaveIdRequest request) {
        requireUser(authentication);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.resolve(request.waveId()));
    }

    private UUID requireUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return user.getId();
    }

    public record ResolveWaveIdRequest(@NotBlank String waveId) {}
}
