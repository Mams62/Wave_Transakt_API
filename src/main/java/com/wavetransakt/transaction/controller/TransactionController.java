package com.wavetransakt.transaction.controller;

import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import com.wavetransakt.transaction.service.TransactionHistoryService;
import com.wavetransakt.transaction.service.TransactionService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.service.WemaSettlementGuard;
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
    private final WemaSettlementGuard settlementGuard;

    /** History remains available while the Wema migration is in progress. */
    @GetMapping
    public ResponseEntity<List<TransactionResponse>> getTransactions(
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                transactionHistoryService.getTransactions(user.getId())
        );
    }

    /**
     * Legacy Wave-to-Wave local projection transfers are closed by default
     * while Wema is the source-of-funds provider.
     */
    @PostMapping("/transfer")
    public ResponseEntity<TransactionResponse> transfer(
            Authentication authentication,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false
            )
            String idempotencyKey,
            @Valid @RequestBody TransferRequest request
    ) {
        settlementGuard.requireMoneyMovementEnabled();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        Object principal = authentication.getPrincipal();
        if (!(principal instanceof User user)) {
            return ResponseEntity.status(401).build();
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
