package com.wavetransakt.merchant.service;

import com.wavetransakt.merchant.dto.PosSessionDtos;
import com.wavetransakt.merchant.entity.Merchant;
import com.wavetransakt.merchant.entity.PosPairingCode;
import com.wavetransakt.merchant.entity.PosTerminal;
import com.wavetransakt.merchant.entity.PosTerminalSession;
import com.wavetransakt.merchant.exception.PosSessionAuthenticationException;
import com.wavetransakt.merchant.repository.PosPairingCodeRepository;
import com.wavetransakt.merchant.repository.PosTerminalLookupRepository;
import com.wavetransakt.merchant.repository.PosTerminalSessionRepository;
import com.wavetransakt.user.entity.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class PosTerminalSessionServiceTest {

    @Test
    void pairingCodeIsStoredOnlyAsSha256Hash() throws Exception {
        UUID ownerId = UUID.randomUUID();
        PosTerminal terminal = terminal(ownerId);

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        PosPairingCodeRepository pairingRepo = mock(PosPairingCodeRepository.class);
        PosTerminalSessionRepository sessionRepo = mock(PosTerminalSessionRepository.class);
        when(terminalRepo.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));

        PosTerminalSessionService service = service(terminalRepo, pairingRepo, sessionRepo);
        PosSessionDtos.PairingCodeResponse response = service.createPairingCode(ownerId, "WTPOS-TEST");

        ArgumentCaptor<PosPairingCode> captor = ArgumentCaptor.forClass(PosPairingCode.class);
        verify(pairingRepo).save(captor.capture());

        PosPairingCode stored = captor.getValue();
        assertEquals(10, response.pairingCode().length());
        assertEquals(64, stored.getCodeHash().length());
        assertNotEquals(response.pairingCode(), stored.getCodeHash());
        assertNull(stored.getConsumedAt());
        assertEquals(ownerId, stored.getCreatedByUserId());
    }

    @Test
    void creatingNewPairingCodeInvalidatesPreviousActiveCode() throws Exception {
        UUID ownerId = UUID.randomUUID();
        PosTerminal terminal = terminal(ownerId);
        PosPairingCode previous = PosPairingCode.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .createdByUserId(ownerId)
                .codeHash("old-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now().minusMinutes(1))
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        PosPairingCodeRepository pairingRepo = mock(PosPairingCodeRepository.class);
        PosTerminalSessionRepository sessionRepo = mock(PosTerminalSessionRepository.class);
        when(terminalRepo.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));
        when(pairingRepo.findAllByTerminalId(terminal.getId())).thenReturn(List.of(previous));

        PosTerminalSessionService service = service(terminalRepo, pairingRepo, sessionRepo);
        service.createPairingCode(ownerId, "WTPOS-TEST");

        assertNotNull(previous.getConsumedAt());
        verify(pairingRepo).save(previous);
        verify(pairingRepo, times(2)).save(any(PosPairingCode.class));
    }

    @Test
    void pairingCodeIsOneTimeAndSessionTokenIsStoredOnlyAsHash() throws Exception {
        UUID ownerId = UUID.randomUUID();
        PosTerminal terminal = terminal(ownerId);
        PosPairingCode pairing = PosPairingCode.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .createdByUserId(ownerId)
                .codeHash("placeholder")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .createdAt(LocalDateTime.now())
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        PosPairingCodeRepository pairingRepo = mock(PosPairingCodeRepository.class);
        PosTerminalSessionRepository sessionRepo = mock(PosTerminalSessionRepository.class);
        when(pairingRepo.findByCodeHash(anyString())).thenReturn(Optional.of(pairing));

        PosTerminalSessionService service = service(terminalRepo, pairingRepo, sessionRepo);
        PosSessionDtos.TerminalSessionResponse response = service.redeem(
                new PosSessionDtos.RedeemPairingRequest("ABCDEFGH23", "Counter POS 1")
        );

        ArgumentCaptor<PosTerminalSession> sessionCaptor = ArgumentCaptor.forClass(PosTerminalSession.class);
        verify(sessionRepo).save(sessionCaptor.capture());
        PosTerminalSession storedSession = sessionCaptor.getValue();

        assertNotNull(pairing.getConsumedAt());
        assertEquals(terminal.getTerminalCode(), response.terminalCode());
        assertFalse(response.sessionToken().isBlank());
        assertEquals(64, storedSession.getTokenHash().length());
        assertNotEquals(response.sessionToken(), storedSession.getTokenHash());
        assertEquals("Counter POS 1", storedSession.getDeviceLabel());

        IllegalArgumentException secondRedeem = assertThrows(
                IllegalArgumentException.class,
                () -> service.redeem(new PosSessionDtos.RedeemPairingRequest("ABCDEFGH23", "Counter POS 1"))
        );
        assertEquals("Invalid or expired pairing code", secondRedeem.getMessage());
        verify(sessionRepo, times(1)).save(any(PosTerminalSession.class));
    }

    @Test
    void revokedTerminalSessionIsRejected() throws Exception {
        UUID ownerId = UUID.randomUUID();
        PosTerminal terminal = terminal(ownerId);
        PosTerminalSession revoked = PosTerminalSession.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .tokenHash("hash")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now().minusMinutes(1))
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        PosPairingCodeRepository pairingRepo = mock(PosPairingCodeRepository.class);
        PosTerminalSessionRepository sessionRepo = mock(PosTerminalSessionRepository.class);
        when(sessionRepo.findByTokenHash(anyString())).thenReturn(Optional.of(revoked));

        PosTerminalSessionService service = service(terminalRepo, pairingRepo, sessionRepo);

        assertThrows(
                PosSessionAuthenticationException.class,
                () -> service.requireValidSession("raw-session-token")
        );
        verify(sessionRepo, never()).save(any(PosTerminalSession.class));
    }

    @Test
    void revokeAllMarksOnlyActiveSessionsRevoked() throws Exception {
        UUID ownerId = UUID.randomUUID();
        PosTerminal terminal = terminal(ownerId);
        PosTerminalSession active = PosTerminalSession.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .tokenHash("active")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .createdAt(LocalDateTime.now())
                .build();
        PosTerminalSession alreadyRevoked = PosTerminalSession.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .tokenHash("revoked")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .revokedAt(LocalDateTime.now().minusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        PosTerminalLookupRepository terminalRepo = mock(PosTerminalLookupRepository.class);
        PosPairingCodeRepository pairingRepo = mock(PosPairingCodeRepository.class);
        PosTerminalSessionRepository sessionRepo = mock(PosTerminalSessionRepository.class);
        PosPairingCode pendingCode = PosPairingCode.builder()
                .id(UUID.randomUUID())
                .terminal(terminal)
                .createdByUserId(ownerId)
                .codeHash("pending")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();

        when(terminalRepo.findByTerminalCode("WTPOS-TEST")).thenReturn(Optional.of(terminal));
        when(sessionRepo.findAllByTerminalId(terminal.getId())).thenReturn(List.of(active, alreadyRevoked));
        when(pairingRepo.findAllByTerminalId(terminal.getId())).thenReturn(List.of(pendingCode));

        PosTerminalSessionService service = service(terminalRepo, pairingRepo, sessionRepo);
        PosSessionDtos.RevokeSessionsResponse response = service.revokeAll(ownerId, "WTPOS-TEST");

        assertEquals(1, response.revokedSessions());
        assertNotNull(active.getRevokedAt());
        assertNotNull(pendingCode.getConsumedAt());
        verify(pairingRepo).save(pendingCode);
        verify(sessionRepo, times(1)).save(active);
        verify(sessionRepo, never()).save(alreadyRevoked);
    }

    private PosTerminal terminal(UUID ownerId) {
        User owner = User.builder().id(ownerId).build();
        Merchant merchant = Merchant.builder()
                .id(UUID.randomUUID())
                .owner(owner)
                .merchantCode("WTM-TEST")
                .businessName("Test Merchant")
                .businessType("RETAIL")
                .build();
        return PosTerminal.builder()
                .id(UUID.randomUUID())
                .merchant(merchant)
                .terminalCode("WTPOS-TEST")
                .build();
    }

    private PosTerminalSessionService service(
            PosTerminalLookupRepository terminalRepo,
            PosPairingCodeRepository pairingRepo,
            PosTerminalSessionRepository sessionRepo
    ) throws Exception {
        PosTerminalSessionService service = new PosTerminalSessionService(terminalRepo, pairingRepo, sessionRepo);
        setLongField(service, "pairingCodeTtlSeconds", 600L);
        setLongField(service, "sessionTtlSeconds", 2_592_000L);
        return service;
    }

    private void setLongField(Object target, String name, long value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.setLong(target, value);
    }
}
