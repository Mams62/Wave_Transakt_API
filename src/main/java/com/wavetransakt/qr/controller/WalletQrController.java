package com.wavetransakt.qr.controller;

import com.wavetransakt.qr.dto.QrTransferRequest;
import com.wavetransakt.qr.dto.WalletQrResponse;
import com.wavetransakt.qr.service.WalletQrService;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class WalletQrController {

    private final WalletQrService walletQrService;
    private final TransactionService transactionService;


    /**
     * ============================================================
     * GET MY WALLET QR
     * ============================================================
     *
     * Returns the authenticated user's permanent wallet QR data.
     *
     * GET /api/v1/qr/wallet
     *
     * Header:
     * Authorization: Bearer <JWT>
     */
    @GetMapping("/wallet")
    public ResponseEntity<?> getMyWalletQr(
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(401)
                    .body("Unauthorized");
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            return ResponseEntity
                    .status(401)
                    .body("Invalid authentication principal");
        }

        try {

            WalletQrResponse response =
                    walletQrService.getWalletQr(
                            user.getId()
                    );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * RESOLVE WALLET QR
     * ============================================================
     *
     * Used when another user scans a wallet QR.
     *
     * GET /api/v1/qr/wallet/{walletNumber}
     *
     * Example:
     *
     * GET /api/v1/qr/wallet/6728895265
     *
     * This DOES NOT transfer money.
     *
     * It only returns information about the recipient wallet.
     */
    @GetMapping("/wallet/{walletNumber}")
    public ResponseEntity<?> resolveWalletQr(
            @PathVariable String walletNumber
    ) {

        try {

            WalletQrResponse response =
                    walletQrService
                            .getWalletQrByWalletNumber(
                                    walletNumber
                            );

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * PAY WALLET QR
     * ============================================================
     *
     * Transfers money to the wallet contained in the QR request.
     *
     * POST /api/v1/qr/wallet/pay
     *
     * Example request:
     *
     * {
     *     "walletNumber": "6728895265",
     *     "amount": 1000,
     *     "description": "QR payment"
     * }
     *
     * The authenticated user becomes the sender.
     */
    @PostMapping("/wallet/pay")
    public ResponseEntity<?> payWalletQr(
            Authentication authentication,
            @Valid @RequestBody QrTransferRequest request
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(401)
                    .body("Unauthorized");
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            return ResponseEntity
                    .status(401)
                    .body("Invalid authentication principal");
        }

        try {

            return ResponseEntity.ok(
                    transactionService.transfer(
                            user.getId(),
                            TransferRequest.builder()
                                    .receiverWalletNumber(
                                            request.getWalletNumber()
                                    )
                                    .amount(
                                            request.getAmount()
                                    )
                                    .description(
                                            request.getDescription()
                                    )
                                    .build()
                    )
            );

        } catch (IllegalArgumentException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }
}