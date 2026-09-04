package com.wavetransakt.qr.service;

import com.wavetransakt.qr.dto.WalletQrResponse;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletQrService {

    private static final String WALLET_QR_PREFIX =
            "WTW:WALLET:";

    private static final String PAYMENT_ENDPOINT =
            "/api/v1/qr/wallet/pay";

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;

    /**
     * Get the permanent wallet QR belonging to the
     * authenticated user.
     *
     * The QR is an identity QR only.
     */
    @Transactional(readOnly = true)
    public WalletQrResponse getWalletQr(
            UUID userId
    ) {

        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "User not found"
                                )
                        );

        Wallet wallet =
                walletRepository
                        .findByUserId(userId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        validateWallet(wallet);

        return buildResponse(
                wallet,
                user
        );
    }

    /**
     * Resolve wallet information using a wallet number.
     *
     * No financial transaction occurs here.
     */
    @Transactional(readOnly = true)
    public WalletQrResponse getWalletQrByWalletNumber(
            String walletNumber
    ) {

        String normalized =
                normalizeWalletNumber(
                        walletNumber
                );

        Wallet wallet =
                walletRepository
                        .findByWalletNumber(normalized)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Wallet not found"
                                )
                        );

        validateWallet(wallet);

        User user = wallet.getUser();

        if (user == null) {
            throw new IllegalArgumentException(
                    "Wallet owner not found"
            );
        }

        return buildResponse(
                wallet,
                user
        );
    }

    /**
     * Resolve a complete permanent QR payload.
     *
     * Example:
     *
     * WTW:WALLET:6728895265
     */
    @Transactional(readOnly = true)
    public WalletQrResponse resolveWalletQr(
            String payload
    ) {

        String walletNumber =
                extractWalletNumber(payload);

        return getWalletQrByWalletNumber(
                walletNumber
        );
    }

    private WalletQrResponse buildResponse(
            Wallet wallet,
            User user
    ) {

        String walletNumber =
                wallet.getWalletNumber();

        String accountName =
                buildAccountName(user);

        return WalletQrResponse.builder()
                .qrType("WALLET")
                .walletNumber(walletNumber)
                .accountName(accountName)
                .currency(wallet.getCurrency())
                .payload(
                        buildWalletPayload(
                                walletNumber
                        )
                )
                .paymentEndpoint(
                        PAYMENT_ENDPOINT
                )
                .build();
    }

    private void validateWallet(
            Wallet wallet
    ) {

        if (wallet == null) {
            throw new IllegalArgumentException(
                    "Wallet not found"
            );
        }

        if (wallet.getStatus() !=
                WalletStatus.ACTIVE) {

            throw new IllegalArgumentException(
                    "Wallet is not active"
            );
        }
    }

    private String buildAccountName(
            User user
    ) {

        String firstName =
                user.getFirstName() == null
                        ? ""
                        : user.getFirstName().trim();

        String lastName =
                user.getLastName() == null
                        ? ""
                        : user.getLastName().trim();

        String accountName =
                (firstName + " " + lastName)
                        .trim();

        if (accountName.isBlank()) {
            throw new IllegalArgumentException(
                    "Account name is unavailable"
            );
        }

        return accountName;
    }

    private String buildWalletPayload(
            String walletNumber
    ) {

        return WALLET_QR_PREFIX +
                normalizeWalletNumber(
                        walletNumber
                );
    }

    private String extractWalletNumber(
            String payload
    ) {

        if (payload == null ||
                payload.isBlank()) {

            throw new IllegalArgumentException(
                    "QR payload is required"
            );
        }

        String normalized =
                payload.trim();

        if (!normalized.startsWith(
                WALLET_QR_PREFIX
        )) {

            throw new IllegalArgumentException(
                    "Invalid Wave Transakt wallet QR"
            );
        }

        String walletNumber =
                normalized.substring(
                        WALLET_QR_PREFIX.length()
                );

        return normalizeWalletNumber(
                walletNumber
        );
    }

    private String normalizeWalletNumber(
            String walletNumber
    ) {

        if (walletNumber == null ||
                walletNumber.isBlank()) {

            throw new IllegalArgumentException(
                    "Wallet number is required"
            );
        }

        return walletNumber.trim();
    }
}