package com.wavetransakt.waveid;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaveIdServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final WalletRepository wallets = mock(WalletRepository.class);
    private final WaveIdService service = new WaveIdService(users, wallets);

    @Test
    void newIdsAreFourLettersFollowedByFourDigitsAndAreNotSequential() {
        String first = WaveIdService.newCandidate();
        assertTrue(first.matches("[A-Z]{4}[0-9]{4}"));
        boolean foundDifferent = false;
        for (int i = 0; i < 30; i++) {
            String candidate = WaveIdService.newCandidate();
            assertTrue(candidate.matches("[A-Z]{4}[0-9]{4}"));
            if (!candidate.equals(first)) foundDifferent = true;
        }
        assertTrue(foundDifferent);
    }

    @Test
    void anonymousOrMalformedHandleCannotTriggerRecipientDatabaseLookup() {
        assertThrows(IllegalArgumentException.class, () -> service.resolve("AB12"));
        assertThrows(IllegalArgumentException.class, () -> service.resolve("abc!1234"));
        verifyNoInteractions(users, wallets);
    }

    @Test
    void cannotActivateBeforeVerifiedAccountIsActive() {
        UUID id = UUID.randomUUID();
        User pending = User.builder()
                .id(id).firstName("Test").lastName("User")
                .accountStatus(AccountStatus.PENDING)
                .bvnVerified(true).ninVerified(true).build();
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(pending));

        assertThrows(IllegalStateException.class, () -> service.activate(id));
        verify(users, never()).saveAndFlush(any(User.class));
        verifyNoInteractions(wallets);
    }

    @Test
    void existingWaveIdIsStableAcrossRepeatedActivation() {
        UUID id = UUID.randomUUID();
        User active = User.builder().id(id).firstName("Test").lastName("User")
                .waveId("ABCD1234").accountStatus(AccountStatus.ACTIVE)
                .bvnVerified(true).ninVerified(true).build();
        when(users.findByIdForUpdate(id)).thenReturn(Optional.of(active));
        assertEquals("ABCD1234", service.activate(id).waveId());
        assertEquals("ABCD1234", service.activate(id).waveId());
        verify(users, never()).saveAndFlush(any(User.class));
    }

    @Test
    void resolveOnlyShowsActiveRecipientAndDoesNotEnablePayment() {
        UUID id = UUID.randomUUID();
        User active = User.builder().id(id).firstName("Ada").lastName("User")
                .waveId("ABCD1234").accountStatus(AccountStatus.ACTIVE).build();
        Wallet wallet = Wallet.builder().user(active)
                .walletNumber("08012345678").status(WalletStatus.ACTIVE).build();
        when(users.findByWaveId("ABCD1234")).thenReturn(Optional.of(active));
        when(wallets.findByUserId(id)).thenReturn(Optional.of(wallet));

        WaveIdService.WaveIdRecord response = service.resolve(" abcd1234 ");
        assertEquals("ABCD1234", response.waveId());
        assertEquals("08012345678", response.walletNumber());
        assertEquals("Ada User", response.displayName());
        assertFalse(response.paymentsEnabled());
        verify(users, never()).save(any(User.class));
    }

    @Test
    void inactiveRecipientCannotBeDiscovered() {
        User inactive = User.builder().id(UUID.randomUUID())
                .waveId("ABCD1234").accountStatus(AccountStatus.PENDING).build();
        when(users.findByWaveId("ABCD1234")).thenReturn(Optional.of(inactive));

        assertThrows(IllegalArgumentException.class, () -> service.resolve("ABCD1234"));
        verifyNoInteractions(wallets);
    }
}
