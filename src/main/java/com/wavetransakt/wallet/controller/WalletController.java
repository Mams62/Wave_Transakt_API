package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.service.WemaWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WemaWalletService wemaWalletService;

    /**
     * Return the authenticated user's wallet.
     *
     * For this controlled build, Wema is the external wallet provider. Once a
     * Wema NUBAN exists, the displayed available balance is refreshed from Wema
     * rather than created locally.
     */
    @GetMapping
    public ResponseEntity<WalletResponse> getWallet(
            Authentication authentication
    ) {
        if (authentication == null ||
                !authentication.isAuthenticated() ||
                !(authentication.getPrincipal() instanceof User user)) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                wemaWalletService.getWallet(user.getId())
        );
    }
}
