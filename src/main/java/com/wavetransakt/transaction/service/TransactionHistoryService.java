package com.wavetransakt.transaction.service;

import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.entity.Transaction;
import com.wavetransakt.transaction.repository.TransactionRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TransactionHistoryService {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactions(
            UUID userId
    ) {

        if (userId == null) {
            throw new IllegalArgumentException(
                    "User is required"
            );
        }

        Wallet wallet = walletRepository
                .findByUserId(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Wallet not found"
                        )
                );

        /*
         * Return the most recent transactions involving
         * the authenticated user's wallet.
         *
         * We limit the initial mobile API to 100 records.
         * Pagination can be added later.
         */
        return transactionRepository
                .findBySenderWalletIdOrReceiverWalletId(
                        wallet.getId(),
                        wallet.getId(),
                        PageRequest.of(
                                0,
                                100,
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "createdAt"
                                )
                        )
                )
                .getContent()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TransactionResponse toResponse(
            Transaction transaction
    ) {

        String senderWalletNumber = null;
        String receiverWalletNumber = null;

        if (transaction.getSenderWallet() != null) {
            senderWalletNumber =
                    transaction
                            .getSenderWallet()
                            .getWalletNumber();
        }

        if (transaction.getReceiverWallet() != null) {
            receiverWalletNumber =
                    transaction
                            .getReceiverWallet()
                            .getWalletNumber();
        }

        return TransactionResponse.builder()
                .id(transaction.getId())
                .reference(transaction.getReference())
                .senderWalletNumber(senderWalletNumber)
                .receiverWalletNumber(receiverWalletNumber)
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .type(transaction.getType())
                .status(transaction.getStatus())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .build();
    }
}