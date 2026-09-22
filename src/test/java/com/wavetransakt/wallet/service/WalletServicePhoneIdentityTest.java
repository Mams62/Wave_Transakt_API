package com.wavetransakt.wallet.service;

import com.wavetransakt.ledger.service.LedgerService;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServicePhoneIdentityTest {

    @Mock WalletRepository walletRepository;
    @Mock LedgerService ledgerService;

    @Test
    void newWaveWalletUsesCanonicalPhoneAsWalletNumber() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .phone("+2348012345678")
                .build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.existsByWalletNumber("08012345678")).thenReturn(false);
        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WalletService service = new WalletService(walletRepository, ledgerService);
        Wallet result = service.createWallet(user);

        assertEquals("08012345678", result.getWalletNumber());

        ArgumentCaptor<Wallet> saved = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(saved.capture());
        assertEquals("08012345678", saved.getValue().getWalletNumber());
        verify(ledgerService).ensureWalletAccount(saved.getValue());
    }

    @Test
    void existingCanonicalPhoneCannotBeAssignedToAnotherWallet() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .phone("08012345678")
                .build();

        when(walletRepository.findByUserId(userId)).thenReturn(Optional.empty());
        when(walletRepository.existsByWalletNumber("08012345678")).thenReturn(true);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new WalletService(walletRepository, ledgerService).createWallet(user)
        );

        assertTrue(error.getMessage().contains("already linked"));
        verify(walletRepository, never()).save(any());
        verifyNoInteractions(ledgerService);
    }
}
