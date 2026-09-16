package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.security.TransactionPinGuard;
import com.wavetransakt.serviceprovider.repository.ServicePaymentRepository;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServicePaymentPinAuthorizationTest {

    @Mock WalletRepository walletRepository;
    @Mock ServicePaymentRepository servicePaymentRepository;
    @Mock TransactionPinGuard transactionPinGuard;
    @Mock ServicePaymentLedgerService servicePaymentLedgerService;
    @Mock ServiceFulfillmentCrypto fulfillmentCrypto;

    @Test
    void rejectedPinStopsBeforeServiceReservation() {
        UUID userId = UUID.randomUUID();
        ServicePaymentReservationService.CanonicalServiceRequest request =
                new ServicePaymentReservationService.CanonicalServiceRequest(
                        "AIRTIME",
                        "mtn",
                        "MTN Airtime",
                        null,
                        "08000000000",
                        "08000000000",
                        null,
                        new BigDecimal("100.00"),
                        "000000"
                );
        doThrow(new IllegalArgumentException("Invalid transaction PIN"))
                .when(transactionPinGuard).verify(userId, "000000");

        ServicePaymentReservationService service =
                new ServicePaymentReservationService(
                        walletRepository,
                        servicePaymentRepository,
                        transactionPinGuard,
                        servicePaymentLedgerService,
                        fulfillmentCrypto
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(userId, "idem-12345678", request)
        );

        verify(transactionPinGuard).verify(userId, "000000");
        verifyNoInteractions(
                walletRepository,
                servicePaymentRepository,
                servicePaymentLedgerService,
                fulfillmentCrypto
        );
    }
}
