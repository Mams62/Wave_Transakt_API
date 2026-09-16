package com.wavetransakt.transaction.service;

import com.wavetransakt.security.TransactionPinGuard;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Public/internal authorization boundary for Wave-to-Wave transfers.
 *
 * TransactionService remains responsible for idempotency, wallet locking,
 * balance mutation and ledger posting. This wrapper makes transaction-PIN
 * authorization mandatory before any caller reaches that financial executor.
 */
@Service
@RequiredArgsConstructor
public class AuthorizedTransferService {

    private final TransactionPinGuard transactionPinGuard;
    private final TransactionService transactionService;

    public TransactionResponse transfer(
            UUID senderUserId,
            String idempotencyKey,
            TransferRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Transfer request is required");
        }

        transactionPinGuard.verify(senderUserId, request.getTransactionPin());

        return transactionService.transfer(
                senderUserId,
                idempotencyKey,
                request
        );
    }
}
