package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.dto.PosDtos;
import com.wavetransakt.merchant.entity.Merchant;
import com.wavetransakt.merchant.entity.MerchantStatus;
import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalStatus;
import com.wavetransakt.merchant.repository.MerchantQrAddressRepository;
import com.wavetransakt.merchant.repository.MerchantSettlementBatchRepository;
import com.wavetransakt.merchant.repository.PosPaymentLookupRepository;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import com.wavetransakt.user.entity.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PosClientServiceTest {

    @Test
    void pendingUnlinkedTerminalCannotReportPaymentAcceptanceEnabled() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).build();
        Merchant merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .merchantCode("WTM-TEST")
                .businessName("Test Merchant")
                .businessType("RETAIL")
                .status(MerchantStatus.DRAFT)
                .build();
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.PENDING_PROVIDER_LINK)
                .supportsQr(true)
                .supportsNfc(false)
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        when(terminalRepo.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));

        PosClientService service = service(terminalRepo);
        PosDtos.PosProfileResponse response = service.getProfile(ownerId, "WTPOS-TEST");

        assertFalse(response.paymentAcceptanceEnabled());
        assertFalse(response.providerLinked());
        assertFalse(response.nfcEnabled());
    }

    @Test
    void terminalOwnedByAnotherUserIsNotExposed() {
        UUID actualOwnerId = UUID.randomUUID();
        UUID requestingOwnerId = UUID.randomUUID();
        User actualOwner = User.builder().id(actualOwnerId).build();
        Merchant merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .owner(actualOwner)
                .merchantCode("WTM-TEST")
                .businessName("Test Merchant")
                .businessType("RETAIL")
                .status(MerchantStatus.ACTIVE)
                .build();
        PosTerminal terminal = PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .status(PosTerminalStatus.ACTIVE)
                .providerCode("INTERSWITCH")
                .providerTerminalId("PROVIDER-TID")
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        when(terminalRepo.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));

        PosClientService service = service(terminalRepo);

        assertThrows(IllegalArgumentException.class,
                () -> service.getProfile(requestingOwnerId, "WTPOS-TEST"));
    }

    private PosClientService service(PosTerminalLookupRepository terminalRepo) {
        return new PosClientService(
                terminalRepo,
                mock(PosPaymentLookupRepository.class),
                mock(MerchantQrAddressRepository.class),
                mock(MerchantSettlementBatchRepository.class),
                new MerchantReceiptPolicy()
        );
    }
}
