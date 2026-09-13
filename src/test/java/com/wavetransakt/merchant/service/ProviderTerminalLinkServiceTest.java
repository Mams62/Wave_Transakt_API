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
    void realProviderAssignmentActivatesTerminalButNfcNeedsExplicitGrant() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .supportsQr(true)
                .supportsNfc(false)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        PosTerminal linked = service.applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId,
                        "interswitch",
                        "REAL-TID-001",
                        false
                )
        );

        assertEquals("INTERSWITCH", linked.getProviderCode());
        assertEquals("REAL-TID-001", linked.getProviderTerminalId());
        assertEquals(PosTerminalStatus.ACTIVE, linked.getStatus());
        assertFalse(linked.isSupportsNfc());
    }

    @Test
    void explicitContactlessGrantEnablesNfc() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .supportsNfc(false)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));
        when(repo.save(any(PosTerminal.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        PosTerminal linked = service.applyProviderAssignment(
                new ProviderTerminalLinkService.ProviderTerminalAssignment(
                        terminalId,
                        "INTERSWITCH",
                        "REAL-TID-002",
                        true
                )
        );

        assertTrue(linked.isSupportsNfc());
        assertEquals(PosTerminalStatus.ACTIVE, linked.getStatus());
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
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(terminal));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);

        assertThrows(
                IllegalStateException.class,
                () -> service.applyProviderAssignment(
                        new ProviderTerminalLinkService.ProviderTerminalAssignment(
                                terminalId,
                                "INTERSWITCH",
                                "TID-DIFFERENT",
                                true
                        )
                )
        );
        verify(repo, never()).save(any(PosTerminal.class));
    }

    @Test
    void retiredTerminalCannotBeRelinkedAndRetirementDisablesNfc() {
        UUID terminalId = UUID.randomUUID();
        PosTerminal retired = PosTerminal.builder()
                .id(terminalId)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.RETIRED)
                .supportsNfc(false)
                .build();

        PosTerminalRepository repo = mock(PosTerminalRepository.class);
        when(repo.findById(terminalId)).thenReturn(Optional.of(retired));

        ProviderTerminalLinkService service = new ProviderTerminalLinkService(repo);
        assertThrows(
                IllegalStateException.class,
                () -> service.applyProviderAssignment(
                        new ProviderTerminalLinkService.ProviderTerminalAssignment(
                                terminalId,
                                "INTERSWITCH",
                                "TID-NEW",
                                true
                        )
                )
        );
    }
}
