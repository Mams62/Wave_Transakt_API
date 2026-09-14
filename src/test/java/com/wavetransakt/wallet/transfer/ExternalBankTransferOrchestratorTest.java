package com.wavetransakt.wallet.transfer;

import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.transfer.BankTransferGateway.Readiness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalBankTransferOrchestratorTest {

    private InterswitchBankTransferGateway gateway;
    private ExternalBankTransferReservationService reservationService;
    private ExternalBankTransferProviderStateService providerStateService;
    private UserRepository userRepository;
    private ExternalBankTransferOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        gateway = mock(InterswitchBankTransferGateway.class);
        reservationService = mock(ExternalBankTransferReservationService.class);
        providerStateService = mock(ExternalBankTransferProviderStateService.class);
        userRepository = mock(UserRepository.class);
        orchestrator = new ExternalBankTransferOrchestrator(
                gateway,
                reservationService,
                providerStateService,
                userRepository
        );
    }

    @Test
    void executionRemainsLockedEvenWhenProviderIsReady() {
        when(gateway.readiness()).thenReturn(new Readiness(
                "INTERSWITCH",
                true,
                true,
                true,
                true,
                true,
                "READY"
        ));

        ExternalBankTransferOrchestrator.ExecutionReadiness readiness =
                orchestrator.readiness();

        assertFalse(readiness.executionEnabled());
        assertFalse(readiness.ready());
        assertEquals("WAVE_EXECUTION_LOCKED", readiness.status());

        assertThrows(IllegalStateException.class, () -> orchestrator.initiate(
                UUID.randomUUID(),
                "idem-12345678",
                new ExternalBankTransferOrchestrator.InitiateRequest(
                        "058",
                        "0123456789",
                        new BigDecimal("1000.00"),
                        "Test transfer",
                        "123456"
                )
        ));

        verify(gateway, never()).resolveAccount("058", "0123456789");
    }

    @Test
    void executionAlsoRequiresProviderWriteReadiness() {
        ReflectionTestUtils.setField(orchestrator, "executionEnabled", true);
        when(gateway.readiness()).thenReturn(new Readiness(
                "INTERSWITCH",
                true,
                false,
                true,
                false,
                false,
                "AWAITING_PROVIDER_APPROVAL"
        ));

        ExternalBankTransferOrchestrator.ExecutionReadiness readiness =
                orchestrator.readiness();

        assertFalse(readiness.ready());
        assertEquals("AWAITING_PROVIDER_APPROVAL", readiness.status());

        assertThrows(IllegalStateException.class, () -> orchestrator.initiate(
                UUID.randomUUID(),
                "idem-12345678",
                new ExternalBankTransferOrchestrator.InitiateRequest(
                        "058",
                        "0123456789",
                        new BigDecimal("1000.00"),
                        null,
                        "123456"
                )
        ));

        verify(reservationService, never()).reserve(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}
