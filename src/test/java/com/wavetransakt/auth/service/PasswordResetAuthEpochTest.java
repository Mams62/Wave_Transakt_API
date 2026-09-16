package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.ResetPasswordRequest;
import com.wavetransakt.auth.entity.PasswordResetToken;
import com.wavetransakt.auth.repository.PasswordResetTokenRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.service.SmsOtpSender;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetAuthEpochTest {

    @Mock UserRepository userRepository;
    @Mock PasswordResetTokenRepository tokenRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock SmsOtpSender smsOtpSender;

    @Test
    void recoveryTokenConsumptionUsesPessimisticWriteLock() throws Exception {
        Lock lock = PasswordResetTokenRepository.class
                .getMethod(
                        "findTopByUserAndUsedFalseOrderByCreatedAtDesc",
                        User.class
                )
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void userEpochChangeUsesPessimisticWriteLock() throws Exception {
        Lock lock = UserRepository.class
                .getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void successfulAccountPinResetAdvancesAuthEpochAtomically() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .phone("08000000000")
                .password("old-pin-hash")
                .authVersion(4L)
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .code("recovery-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setIdentifier("person@example.com");
        request.setCode("123456");
        request.setNewPassword("654321");

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("123456", "recovery-hash")).thenReturn(true);
        when(passwordEncoder.matches("654321", "old-pin-hash")).thenReturn(false);
        when(passwordEncoder.encode("654321")).thenReturn("new-pin-hash");

        service().resetPassword(request);

        assertEquals("new-pin-hash", user.getPassword());
        assertEquals(5L, user.getAuthVersion());
        assertTrue(token.isUsed());
        verify(userRepository).findByIdForUpdate(user.getId());
        verify(userRepository).save(user);
        verify(tokenRepository).save(token);
    }

    @Test
    void failedRecoveryCodeDoesNotAdvanceEpoch() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .phone("08000000000")
                .password("old-pin-hash")
                .authVersion(9L)
                .build();

        PasswordResetToken token = PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(user)
                .code("recovery-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setIdentifier("person@example.com");
        request.setCode("000000");
        request.setNewPassword("654321");

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(tokenRepository.findTopByUserAndUsedFalseOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.matches("000000", "recovery-hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service().resetPassword(request));

        assertEquals(9L, user.getAuthVersion());
        assertFalse(token.isUsed());
        verify(userRepository).findByIdForUpdate(user.getId());
        verify(userRepository, never()).save(any());
        verify(tokenRepository, never()).save(any());
    }

    private PasswordResetService service() {
        return new PasswordResetService(
                userRepository,
                tokenRepository,
                passwordEncoder,
                smsOtpSender
        );
    }
}
