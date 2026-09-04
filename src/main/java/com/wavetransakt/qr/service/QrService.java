package com.wavetransakt.qr.service;

import com.wavetransakt.qr.dto.QrResolveResponse;
import com.wavetransakt.qr.dto.QrWalletResponse;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QrService {

    private static final String QR_PREFIX = "WT://wallet/";

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    /**
     * Return the permanent QR identity for the authenticated user's wallet.
     */
    public QrWalletResponse getMyWalletQr(UUID userId) {

        User user = getUser(userId);

        Wallet wallet = walletRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );

        boolean active =
                wallet.getStatus() != null &&
                wallet.getStatus().name().equals("ACTIVE");

        return QrWalletResponse.builder()
                .qrType("WALLET")
                .qrCode(buildWalletQrCode(wallet))
                .accountName(
                        buildAccountName(user)
                )
                .walletNumber(wallet.getWalletNumber())
                .currency(wallet.getCurrency())
                .active(active)
                .build();
    }

    /**
     * Resolve a scanned wallet QR.
     *
     * The QR contains only the wallet UUID.
     * Current wallet/account information is loaded from the database.
     */
    public QrResolveResponse resolveWalletQr(String qrCode) {

        UUID walletId = extractWalletId(qrCode);

        Wallet wallet = walletRepository
                .findById(walletId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet QR code not found"
                        )
                );

        User user = wallet.getUser();

        if (user == null) {
            throw new IllegalArgumentException(
                    "Wallet owner not found"
            );
        }

        boolean active =
                wallet.getStatus() != null &&
                wallet.getStatus().name().equals("ACTIVE");

        return QrResolveResponse.builder()
                .qrType("WALLET")
                .accountName(buildAccountName(user))
                .walletNumber(wallet.getWalletNumber())
                .currency(wallet.getCurrency())
                .active(active)
                .build();
    }

    private String buildWalletQrCode(Wallet wallet) {

        return QR_PREFIX + wallet.getId();
    }

    private UUID extractWalletId(String qrCode) {

        if (qrCode == null || qrCode.isBlank()) {

            throw new IllegalArgumentException(
                    "QR code is required"
            );
        }

        if (!qrCode.startsWith(QR_PREFIX)) {

            throw new IllegalArgumentException(
                    "Invalid Wave Transakt wallet QR"
            );
        }

        String walletId =
                qrCode.substring(QR_PREFIX.length());

        try {

            return UUID.fromString(walletId);

        } catch (IllegalArgumentException e) {

            throw new IllegalArgumentException(
                    "Invalid wallet QR code"
            );
        }
    }

    private User getUser(UUID userId) {

        return userRepository
                .findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "User not found"
                        )
                );
    }

    private String buildAccountName(User user) {

        return user.getFirstName() +
                " " +
                user.getLastName();
    }
}
