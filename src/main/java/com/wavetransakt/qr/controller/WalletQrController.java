package com.wavetransakt.qr.controller;

import com.wavetransakt.qr.dto.QrTransferRequest;
import com.wavetransakt.qr.dto.WalletQrResponse;
import com.wavetransakt.qr.service.WalletQrService;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/qr")
@RequiredArgsConstructor
public class WalletQrController {

    private final WalletQrService walletQrService;
    private final TransactionService transactionService;

    /**
     * Returns the authenticated user's permanent wallet QR.
     *
     * The QR identifies a wallet only.
     * It does NOT authorize a transaction.
     */
    @GetMapping("/wallet")
    public ResponseEntity<?> getMyWalletQr(
            Authentication authentication
    ) {

        User user = requireUser(authentication);

        WalletQrResponse response =
                walletQrService.getWalletQr(
                        user.getId()
                );

        return ResponseEntity.ok(response);
    }

    /**
     * Resolve the wallet represented by a permanent QR.
     *
     * This endpoint NEVER moves money.
     */
    @GetMapping("/wallet/{walletNumber}")
    public ResponseEntity<WalletQrResponse>
    resolveWalletQr(
            @PathVariable String walletNumber
    ) {

        return ResponseEntity.ok(
                walletQrService
                        .getWalletQrByWalletNumber(
                                walletNumber
                        )
        );
    }

    /**
     * Execute a wallet QR payment.
     *
     * The permanent QR identifies the receiver.
     *
     * Idempotency-Key identifies ONE payer instruction.
     *
     * Retrying the same payment with the same key must return
     * the original transaction rather than debit again.
     */
    @PostMapping("/wallet/pay")
    public ResponseEntity<TransactionResponse>
    payWalletQr(
            Authentication authentication,

            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            QrTransferRequest request
    ) {

        User user = requireUser(authentication);

        TransferRequest transferRequest =
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
                        .build();

        TransactionResponse response =
                transactionService.transfer(
                        user.getId(),
                        idempotencyKey,
                        transferRequest
                );

        return ResponseEntity.ok(response);
    }

    private User requireUser(
            Authentication authentication
    ) {

        if (authentication == null ||
                !authentication.isAuthenticated()) {

            throw new IllegalArgumentException(
                    "Authentication required"
            );
        }

        Object principal =
                authentication.getPrincipal();

        if (!(principal instanceof User user)) {

            throw new IllegalArgumentException(
                    "Invalid authentication principal"
            );
        }

        return user;
    }
}