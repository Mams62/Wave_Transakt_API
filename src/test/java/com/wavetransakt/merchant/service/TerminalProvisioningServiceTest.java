package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.entity.Merchant;
import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalStatus;
import com.wavetransakt.merchant.provider.MerchantAcquiringGateway;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TerminalProvisioningServiceTest {

    @Test
    void successfulGatewayLinkAppliesProviderCardAndContactlessGrants() {
        UUID terminalId = UUID.randomUUID();
        Merchant merchant = Merchant.builder().merchantCode("WTM-TEST").build();
        PosTerminal terminal = PosTerminal.builder()
                .id(terminalId)
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        when(gateway.linkTerminal(any())).thenReturn(
                new MerchantAcquiringGateway.TerminalLinkResult(
                        "INTERSWITCH", "PROVIDER-TID-001", "LINKED",
                        true, true, "provider linked"
                )
        );

        PosTerminalLookupRepository lookup = mock(PosTerminalLookupRepository.class);
        when(lookup.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));

        ProviderTerminalLinkService linkService = mock(ProviderTerminalLinkService.class);
        when(linkService.applyProviderAssignment(any())).thenAnswer(invocation -> {
            ProviderTerminalLinkService.ProviderTerminalAssignment assignment = invocation.getArgument(0);
            terminal.setProviderCode(assignment.providerCode());
            terminal.setProviderTerminalId(assignment.providerTerminalId());
            terminal.setSupportsCard(assignment.cardAcceptanceApproved());
            terminal.setSupportsNfc(assignment.cardAcceptanceApproved() && assignment.contactlessApproved());
            terminal.setStatus(PosTerminalStatus.ACTIVE);
            return terminal;
        });

        TerminalProvisioningService service = new TerminalProvisioningService(List.of(gateway), lookup, linkService);
        TerminalProvisioningService.ProvisioningOutcome result = service.provision(
                "interswitch", "WTPOS-TEST", "SERIAL-001"
        );

        assertEquals("INTERSWITCH", result.providerCode());
        assertEquals("PROVIDER-TID-001", result.providerTerminalId());
        assertTrue(result.cardEnabled());
        assertTrue(result.contactlessEnabled());
        assertEquals("ACTIVE", result.terminalStatus());
        verify(linkService).applyProviderAssignment(any());
    }

    @Test
    void missingProviderTerminalIdIsRejectedBeforeStateMutation() {
        Merchant merchant = Merchant.builder().merchantCode("WTM-TEST").build();
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        when(gateway.linkTerminal(any())).thenReturn(
                new MerchantAcquiringGateway.TerminalLinkResult(
                        "INTERSWITCH", " ", "LINKED", true, true, "provider linked"
                )
        );

        PosTerminalLookupRepository lookup = mock(PosTerminalLookupRepository.class);
        when(lookup.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));
        ProviderTerminalLinkService linkService = mock(ProviderTerminalLinkService.class);

        TerminalProvisioningService service = new TerminalProvisioningService(List.of(gateway), lookup, linkService);
        assertThrows(IllegalStateException.class, () ->
                service.provision("INTERSWITCH", "WTPOS-TEST", "SERIAL-001")
        );
        verify(linkService, never()).applyProviderAssignment(any());
    }

    @Test
    void mismatchedProviderResultIsRejected() {
        Merchant merchant = Merchant.builder().merchantCode("WTM-TEST").build();
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .build();

        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        when(gateway.linkTerminal(any())).thenReturn(
                new MerchantAcquiringGateway.TerminalLinkResult(
                        "OTHER", "TID-1", "LINKED", true, false, "provider linked"
                )
        );

        PosTerminalLookupRepository lookup = mock(PosTerminalLookupRepository.class);
        when(lookup.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));
        ProviderTerminalLinkService linkService = mock(ProviderTerminalLinkService.class);

        TerminalProvisioningService service = new TerminalProvisioningService(List.of(gateway), lookup, linkService);
        assertThrows(IllegalStateException.class, () ->
                service.provision("INTERSWITCH", "WTPOS-TEST", "SERIAL-001")
        );
        verify(linkService, never()).applyProviderAssignment(any());
    }

    @Test
    void nonPendingTerminalCannotBeProvisionedAgain() {
        Merchant merchant = Merchant.builder().merchantCode("WTM-TEST").build();
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.ACTIVE)
                .build();

        MerchantAcquiringGateway gateway = mock(MerchantAcquiringGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        PosTerminalLookupRepository lookup = mock(PosTerminalLookupRepository.class);
        when(lookup.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));
        ProviderTerminalLinkService linkService = mock(ProviderTerminalLinkService.class);

        TerminalProvisioningService service = new TerminalProvisioningService(List.of(gateway), lookup, linkService);
        assertThrows(IllegalStateException.class, () ->
                service.provision("INTERSWITCH", "WTPOS-TEST", "SERIAL-001")
        );
        verify(gateway, never()).linkTerminal(any());
    }
}
