package com.wavetransakt.wallet.dto;

public record WemaWalletActionResponse(
        String status,
        String message,
        String accountNumber
) {
}
