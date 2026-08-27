package com.wavetransakt.service;

import com.wavetransakt.dto.InitializePaymentRequest;
import com.wavetransakt.dto.PaymentResponse;
import com.wavetransakt.model.User;
import com.wavetransakt.model.Wallet;
import com.wavetransakt.model.WalletTransaction;
import com.wavetransakt.repository.UserRepository;
import com.wavetransakt.repository.WalletRepository;
import com.wavetransakt.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class WalletFundingService {
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final PaystackService paystackService;

    public WalletFundingService(UserRepository userRepository,
                                WalletRepository walletRepository,
                                WalletTransactionRepository transactionRepository,
                                PaystackService paystackService) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.paystackService = paystackService;
    }

    @Transactional
    public PaymentResponse initialize(UUID userId, InitializePaymentRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        BigDecimal amount = request.amount().setScale(2, RoundingMode.HALF_UP);
        String reference = "WT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);

        WalletTransaction tx = new WalletTransaction();
        tx.setUser(user);
        tx.setReference(reference);
        tx.setAmount(amount);
        tx.setType(WalletTransaction.Type.CREDIT);
        tx.setStatus(WalletTransaction.Status.PENDING);
        transactionRepository.save(tx);

        Map<String, Object> paystack = paystackService.initialize(user.getEmail(), amount, reference);
        Object dataObject = paystack == null ? null : paystack.get("data");
        if (!(dataObject instanceof Map<?, ?> data)) {
            tx.setStatus(WalletTransaction.Status.FAILED);
            transactionRepository.save(tx);
            throw new IllegalStateException("Paystack initialization failed");
        }

        String authorizationUrl = String.valueOf(data.get("authorization_url"));
        return new PaymentResponse(reference, amount, "PENDING", authorizationUrl,
                walletRepository.findByUserId(userId).map(Wallet::getBalance).orElse(BigDecimal.ZERO));
    }

    @Transactional
    public PaymentResponse verify(UUID userId, String reference) {
        WalletTransaction tx = transactionRepository.findByReference(reference)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
        if (!tx.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("Transaction does not belong to user");
        }

        // Idempotency: a previously credited transaction must never credit the wallet twice.
        if (tx.getStatus() == WalletTransaction.Status.SUCCESS) {
            Wallet wallet = walletRepository.findByUserId(userId)
                    .orElseThrow(() -> new IllegalStateException("Wallet not found"));
            return new PaymentResponse(reference, tx.getAmount(), "SUCCESS", null, wallet.getBalance());
        }

        Map<String, Object> result = paystackService.verify(reference);
        Object dataObject = result == null ? null : result.get("data");
        if (!(dataObject instanceof Map<?, ?> data)) {
            throw new IllegalStateException("Invalid Paystack verification response");
        }

        String status = String.valueOf(data.get("status"));
        long verifiedKobo = numberValue(data.get("amount"));
        long expectedKobo = tx.getAmount().movePointRight(2).longValueExact();

        if (!"success".equalsIgnoreCase(status) || verifiedKobo != expectedKobo) {
            tx.setStatus(WalletTransaction.Status.FAILED);
            transactionRepository.save(tx);
            throw new IllegalArgumentException("Payment could not be verified");
        }

        Wallet wallet = walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new IllegalStateException("Wallet not found"));
        wallet.setBalance(wallet.getBalance().add(tx.getAmount()));
        wallet.touch();
        walletRepository.save(wallet);

        tx.setStatus(WalletTransaction.Status.SUCCESS);
        tx.setCompletedAt(Instant.now());
        transactionRepository.save(tx);

        return new PaymentResponse(reference, tx.getAmount(), "SUCCESS", null, wallet.getBalance());
    }

    private long numberValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }
}
