package com.wavetransakt.wallet.transfer;

import com.wavetransakt.security.TransactionPinGuard;
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
class ExternalBankTransferPinAuthorizationTest {

    @Mock WalletRepository walletRepository;
    @Mock ExternalBankTransferRepository transferRepository;
    @Mock TransactionPinGuard transactionPinGuard;
    @Mock ExternalBankTransferLedgerService ledgerService;

    @Test
    void rejectedPinStopsBeforeWalletReservation() {
        UUID userId = UUID.randomUUID();
        ExternalBankTransferReservationService.CanonicalTransferRequest request =
                new ExternalBankTransferReservationService.CanonicalTransferRequest(
                        "058",
                        "0123456789",
                        "Test Recipient",
                        new BigDecimal("1000.00"),
                        "Test",
                        "000000"
                );
        doThrow(new IllegalArgumentException("Invalid transaction PIN"))
                .when(transactionPinGuard).verify(userId, "000000");

        ExternalBankTransferReservationService service =
                new ExternalBankTransferReservationService(
                        walletRepository,
                        transferRepository,
                        transactionPinGuard,
                        ledgerService
                );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.reserve(userId, "idem-12345678", request)
        );

        verify(transactionPinGuard).verify(userId, "000000");
        verifyNoInteractions(walletRepository, transferRepository, ledgerService);
    }
}
