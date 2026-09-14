package com.wavetransakt.card.service;

import com.wavetransakt.card.provider.CardIssuingGateway;
import com.wavetransakt.card.provider.CardIssuingOperationException;
import com.wavetransakt.card.repository.WaveCardRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WaveCardServiceTest {

    @Test
    void failedProviderIssuanceNeverCreatesLocalCard() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).firstName("Wave").lastName("User")
                .email("wave@example.com").phone("08000000000").password("hash").build();
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).user(user).walletNumber("WT1234567890").build();

        CardIssuingGateway gateway = mock(CardIssuingGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        when(gateway.createCard(any())).thenThrow(new CardIssuingOperationException("provider unavailable"));

        WaveCardRepository cardRepository = mock(WaveCardRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WaveCardService service = new WaveCardService(
                List.of(gateway), cardRepository, userRepository, walletRepository
        );

        assertThrows(
                CardIssuingOperationException.class,
                () -> service.requestCard(user, "INTERSWITCH", null)
        );

        verify(cardRepository, never()).save(any());
    }

    @Test
    void providerResultWithoutOpaqueReferenceIsRejected() {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).firstName("Wave").lastName("User")
                .email("wave2@example.com").phone("08000000001").password("hash").build();
        Wallet wallet = Wallet.builder().id(UUID.randomUUID()).user(user).walletNumber("WT1234567891").build();

        CardIssuingGateway gateway = mock(CardIssuingGateway.class);
        when(gateway.code()).thenReturn("INTERSWITCH");
        when(gateway.createCard(any())).thenReturn(
                new CardIssuingGateway.CardholderLinkResult(
                        "INTERSWITCH", null, null, "PENDING", false
                )
        );

        WaveCardRepository cardRepository = mock(WaveCardRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        WalletRepository walletRepository = mock(WalletRepository.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(walletRepository.findByUserId(userId)).thenReturn(Optional.of(wallet));

        WaveCardService service = new WaveCardService(
                List.of(gateway), cardRepository, userRepository, walletRepository
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.requestCard(user, "INTERSWITCH", "WAVE_CONTACTLESS")
        );

        verify(cardRepository, never()).save(any());
    }
}
