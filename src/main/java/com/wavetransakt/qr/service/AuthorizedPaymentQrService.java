package com.wavetransakt.qr.service;

import com.wavetransakt.security.TransactionPinGuard;
import com.wavetransakt.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Authorization boundary for one-time payment-QR consumption.
 * Resolve/create operations remain separate because they do not move money.
 */
@Service
@RequiredArgsConstructor
public class AuthorizedPaymentQrService {

    private final TransactionPinGuard transactionPinGuard;
    private final PaymentQrService paymentQrService;

    public TransactionResponse pay(
            UUID payerUserId,
            String idempotencyKey,
            String payload,
            String transactionPin
    ) {
        transactionPinGuard.verify(payerUserId, transactionPin);
        return paymentQrService.pay(payerUserId, idempotencyKey, payload);
    }
}
