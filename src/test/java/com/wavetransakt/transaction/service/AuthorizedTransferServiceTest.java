package com.wavetransakt.transaction.service;

import com.wavetransakt.security.TransactionPinGuard;
import com.wavetransakt.transaction.dto.TransactionResponse;
import com.wavetransakt.transaction.dto.TransferRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizedTransferServiceTest {

    @Mock TransactionPinGuard transactionPinGuard;
    @Mock TransactionService transactionService;

    @Test
    void pinAuthorizationHappensBeforeFinancialExecutor() {
        UUID userId = UUID.randomUUID();
        TransferRequest request = TransferRequest.builder()
                .receiverWalletNumber("WT00000001")
                .amount(new BigDecimal("100.00"))
                .description("test")
                .transactionPin("123456")
                .build();
        TransactionResponse expected = TransactionResponse.builder().reference("WT-TEST").build();
        when(transactionService.transfer(userId, "idem-12345678", request)).thenReturn(expected);

        AuthorizedTransferService service = new AuthorizedTransferService(
                transactionPinGuard,
                transactionService
        );

        TransactionResponse result = service.transfer(userId, "idem-12345678", request);

        assertSame(expected, result);
        InOrder order = inOrder(transactionPinGuard, transactionService);
        order.verify(transactionPinGuard).verify(userId, "123456");
        order.verify(transactionService).transfer(userId, "idem-12345678", request);
    }

    @Test
    void failedPinNeverReachesFinancialExecutor() {
        UUID userId = UUID.randomUUID();
        TransferRequest request = TransferRequest.builder()
                .receiverWalletNumber("WT00000001")
                .amount(new BigDecimal("100.00"))
                .transactionPin("000000")
                .build();
        doThrow(new IllegalArgumentException("Invalid transaction PIN"))
                .when(transactionPinGuard).verify(userId, "000000");

        AuthorizedTransferService service = new AuthorizedTransferService(
                transactionPinGuard,
                transactionService
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.transfer(userId, "idem-12345678", request)
        );

        verifyNoInteractions(transactionService);
    }
}
