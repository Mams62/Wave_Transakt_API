package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalStatus;
import com.wavetransakt.merchant.repository.PosTerminalRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderTerminalLinkServiceTest {

    @Test
    void providerLinkCanActivateTerminalWithoutGrantingCardOrNfc() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .supportsQr(true)
                .supportsCard(false)
                .supportsNfc(false)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        PosTerminal linked = service.applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId, "interswitch", "REAL-TID-001", false, false
                )
        );

        assertEquals("INTERSWITCH", linked.getProviderCode());
        assertEquals("REAL-TID-001", linked.getProviderTerminalId());
        assertEquals(PosTerminalStatus.ACTIVE, linked.getStatus());
        assertFalse(linked.isSupportsCard());
        assertFalse(linked.isSupportsNfc());
    }

    @Test
    void cardGrantCanBeEnabledWithoutContactless() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PosTerminal linked = new ProviderTerminalLinkService(repo).applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId, "INTERSWITCH", "REAL-TID-002", true, false
                )
        );

        assertTrue(linked.isSupportsCard());
        assertFalse(linked.isSupportsNfc());
    }

    @Test
    void contactlessCannotTurnOnWithoutCardApproval() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PosTerminal linked = new ProviderTerminalLinkService(repo).applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId, "INTERSWITCH", "REAL-TID-003", false, true
                )
        );

        assertFalse(linked.isSupportsCard());
        assertFalse(linked.isSupportsNfc());
    }

    @Test
    void explicitCardAndContactlessGrantEnablesBoth() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PosTerminal linked = new ProviderTerminalLinkService(repo).applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId, "INTERSWITCH", "REAL-TID-004", true, true
                )
        );

        assertTrue(linked.isSupportsCard());
        assertTrue(linked.isSupportsNfc());
    }

    @Test
    void differentProviderAssignmentCannotOverwriteExistingLink() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .providerCode("INTERSWITCH")
                .providerTerminalId("TID-OLD")
                .status(PosTerminalStatus.ACTIVE)
                .supportsCard(true)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        assertThrows(
                IllegalStateException.class,
                () -> service.applyProviderAssignment(
                        new ProviderTerminalLinkService.ProviderTerminalAssignment(
                                terminalId, "INTERSWITCH", "TID-DIFFERENT", true, true
                        )
                )
        );
        verify(repo, never()).save(any(PosTerminal.class));
    }

    @Test
    void retirementDisablesCardAndNfcAndRetiredTerminalCannotBeRelinked() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.ACTIVE)
                .supportsCard(true)
                .supportsNfc(true)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        PosTerminal retired = service.retire(terminalId);
        assertEquals(PosTerminalStatus.RETIRED, retired.getStatus());
        assertFalse(retired.isSupportsCard());
        assertFalse(retired.isSupportsNfc());

        assertThrows(
                IllegalStateException.class,
                () -> service.applyProviderAssignment(
                        new ProviderTerminalLinkService.ProviderTerminalAssignment(
                                terminalId, "INTERSWITCH", "TID-NEW", true, true
                        )
                )
        );
    }
}
