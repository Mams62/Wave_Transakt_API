package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Get the authenticated user's wallet.
     */
    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        UUID userId = user.getId();

        return ResponseEntity.ok(
                walletService.getWallet(userId)
        );
    }

    /**
     * Development/testing wallet funding endpoint.
     *
     * This adds funds directly to the authenticated
     * user's wallet.
     *
     * IMPORTANT:
     * This endpoint is for development/testing only.
     * It should not be exposed in production.
     */
    @PostMapping("/fund")
    public ResponseEntity<WalletResponse> fundWallet(
            Authentication authentication,
            @RequestParam BigDecimal amount
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            return ResponseEntity
                    .status(401)
                    .build();
        }

        return ResponseEntity.ok(
                walletService.fundWallet(
                        user.getId(),
                        amount
                )
        );
    }
}