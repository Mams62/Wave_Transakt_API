package com.wavetransakt.controller;

import com.wavetransakt.model.Wallet;
import com.wavetransakt.model.WalletTransaction;
import com.wavetransakt.repository.WalletRepository;
import com.wavetransakt.repository.WalletTransactionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class WalletController {
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;

    public WalletController(
            WalletRepository wallets,
            WalletTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @GetMapping("/wallet")
    @Transactional(readOnly = true)
    public ResponseEntity<WalletResponse> wallet(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = authenticatedUserId(jwt);
        Wallet wallet = wallets.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        return ResponseEntity.ok(new WalletResponse(
                wallet.getId(),
                wallet.getUser().getWalletNumber(),
                wallet.getBalance(),
                "NGN",
                "ACTIVE"
        ));
    }

    @GetMapping("/transactions")
    @Transactional(readOnly = true)
    public ResponseEntity<List<TransactionResponse>> transactions(@AuthenticationPrincipal Jwt jwt) {
        UUID userId = authenticatedUserId(jwt);
        List<TransactionResponse> response = transactions
                .findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(response);
    }

    private TransactionResponse toResponse(WalletTransaction transaction) {
        String walletNumber = transaction.getUser().getWalletNumber();
        boolean credit = transaction.getType() == WalletTransaction.Type.CREDIT;

        return new TransactionResponse(
                transaction.getId(),
                transaction.getReference(),
                credit ? null : walletNumber,
                credit ? walletNumber : null,
                transaction.getAmount(),
                "NGN",
                transaction.getType().name(),
                transaction.getStatus().name(),
                null,
                transaction.getCreatedAt()
        );
    }

    private UUID authenticatedUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication subject");
        }
    }

    public record WalletResponse(
            UUID id,
            String walletNumber,
            BigDecimal balance,
            String currency,
            String status) {}

    public record TransactionResponse(
            UUID id,
            String reference,
            String senderWalletNumber,
            String receiverWalletNumber,
            BigDecimal amount,
            String currency,
            String type,
            String status,
            String description,
            Instant createdAt) {}
}
