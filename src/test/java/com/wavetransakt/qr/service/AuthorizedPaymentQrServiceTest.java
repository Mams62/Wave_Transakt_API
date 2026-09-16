package com.wavetransakt.qr.service;

import com.wavetransakt.security.TransactionPinGuard;
import com.wavetransakt.transaction.dto.TransactionResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizedPaymentQrServiceTest {

    @Mock TransactionPinGuard transactionPinGuard;
    @Mock PaymentQrService paymentQrService;

    @Test
    void pinAuthorizationHappensBeforeOneTimeQrConsumption() {
        UUID userId = UUID.randomUUID();
        TransactionResponse expected = TransactionResponse.builder().reference("WT-QR").build();
        when(paymentQrService.pay(userId, "idem-12345678", "WTW:PAY:1:payload"))
                .thenReturn(expected);

        AuthorizedPaymentQrService service = new AuthorizedPaymentQrService(
                transactionPinGuard,
                paymentQrService
        );

        TransactionResponse result = service.pay(
                userId,
                "idem-12345678",
                "WTW:PAY:1:payload",
                "123456"
        );

        assertSame(expected, result);
        InOrder order = inOrder(transactionPinGuard, paymentQrService);
        order.verify(transactionPinGuard).verify(userId, "123456");
        order.verify(paymentQrService).pay(userId, "idem-12345678", "WTW:PAY:1:payload");
    }

    @Test
    void failedPinNeverConsumesOneTimeQr() {
        UUID userId = UUID.randomUUID();
        doThrow(new IllegalArgumentException("Invalid transaction PIN"))
                .when(transactionPinGuard).verify(userId, "000000");

        AuthorizedPaymentQrService service = new AuthorizedPaymentQrService(
                transactionPinGuard,
                paymentQrService
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.pay(userId, "idem-12345678", "payload", "000000")
        );

        verifyNoInteractions(paymentQrService);
    }
}
