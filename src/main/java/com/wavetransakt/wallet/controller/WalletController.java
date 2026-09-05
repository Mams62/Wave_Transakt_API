package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * Return the authenticated user's wallet.
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

    /*
     * Direct wallet funding has intentionally been removed.
     *
     * Wallet value must only be created after a verified
     * funding provider event such as Paystack/Wema and must
     * also be represented in the double-entry ledger.
     */
}