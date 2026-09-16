package com.wavetransakt.auth.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionRevocationServiceTest {

    @Mock UserRepository userRepository;

    @Test
    void lockedUserLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = UserRepository.class
                .getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void revokeAllAdvancesEpochExactlyOnceUnderLock() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
                .id(userId)
                .email("person@example.com")
                .phone("08000000000")
                .password("hash")
                .authVersion(11L)
                .build();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);

        new SessionRevocationService(userRepository).revokeAll(userId);

        assertEquals(12L, user.getAuthVersion());
        InOrder order = inOrder(userRepository);
        order.verify(userRepository).findByIdForUpdate(userId);
        order.verify(userRepository).save(user);
        verifyNoMoreInteractions(userRepository);
    }

    @Test
    void missingUserCannotCreateSyntheticEpoch() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new SessionRevocationService(userRepository).revokeAll(userId)
        );

        assertEquals("User account not found", error.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void nullUserIdIsRejectedBeforeDatabaseAccess() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionRevocationService(userRepository).revokeAll(null)
        );
        verifyNoInteractions(userRepository);
    }
}
