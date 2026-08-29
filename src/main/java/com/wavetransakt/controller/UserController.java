package com.wavetransakt.controller;

import com.wavetransakt.model.User;
import com.wavetransakt.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        UUID authenticatedId = UUID.fromString(jwt.getSubject());
        if (!authenticatedId.equals(id)) {
            return ResponseEntity.status(403).body(Map.of("message", "You are not allowed to access this account"));
        }
        User user = authService.findAuthenticatedUser(authenticatedId);
        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "fullName", user.getFullName(),
                "walletNumber", user.getWalletNumber()
        ));
    }
}
