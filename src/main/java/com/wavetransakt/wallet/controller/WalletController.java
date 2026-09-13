package com.wavetransakt.wallet.controller;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.dto.WalletResponse;
import com.wavetransakt.wallet.service.WaveWalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/wallet", "/api/v1/wallet"})
@RequiredArgsConstructor
public class WalletController {

    private final WaveWalletService waveWalletService;

    /**
     * Return the authenticated user's Wave wallet through the provider-neutral
     * wallet boundary. Provider-specific details remain behind the server-side
     * adapter layer so Android, iOS and POS do not depend on Wema/Interswitch.
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
                waveWalletService.getWallet(user.getId())
        );
    }
}
