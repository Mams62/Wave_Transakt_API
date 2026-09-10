package com.wavetransakt.transaction.controller;

import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionHistoryService;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;
    private final TransactionHistoryService transactionHistoryService;

    /**
     * ============================================================
     * TRANSACTION HISTORY
     * ============================================================
     *
     * Android:
     *
     * GET /api/transactions
     *
     * Returns transactions involving the authenticated
     * user's wallet.
     */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions(
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

        return ResponseEntity.ok(
                transactionHistoryService.getTransactions(
                        user.getId()
                )
        );
    }

    /**
     * ============================================================
     * WALLET TRANSFER
     * ============================================================
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            Authentication authentication,

            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,

            @Valid
            @RequestBody
            TransferRequest request
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
                transactionService.transfer(
                        user.getId(),
                        idempotencyKey,
                        request
                )
        );
    }
}