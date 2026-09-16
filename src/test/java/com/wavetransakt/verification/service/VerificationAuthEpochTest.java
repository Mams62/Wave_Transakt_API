package com.wavetransakt.verification.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.verification.repository.VerificationCodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationAuthEpochTest {

    @Mock VerificationCodeRepository verificationCodeRepository;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;

    private VerificationService service;

    @BeforeEach
    void setUp() {
        service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );
    }

    @Test
    void newlyIssuedLoginOtpSnapshotsCurrentAuthEpoch() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .phone("08000000000")
                .authVersion(6L)
                .build();

        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                        user,
                        VerificationType.PHONE
                )).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("otp-hash");

        service.createPhoneVerificationCode(user);

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(verificationCodeRepository).save(captor.capture());
        assertEquals(6L, captor.getValue().getAuthVersion());
        assertEquals(VerificationType.PHONE, captor.getValue().getType());
        assertNull(captor.getValue().getCode());
        assertEquals("otp-hash", captor.getValue().getCodeHash());
    }

    @Test
    void preResetLoginOtpIsRetiredAndRejectedAfterAuthEpochChanges() {
        User user = User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .phone("08000000000")
                .authVersion(2L)
                .build();

        VerificationCode staleCode = VerificationCode.builder()
                .id(UUID.randomUUID())
                .user(user)
                .type(VerificationType.PHONE)
                .codeHash("otp-hash")
                .authVersion(1L)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
                        user,
                        VerificationType.PHONE
                )).thenReturn(Optional.of(staleCode));
        when(passwordEncoder.encode("000000")).thenReturn("dummy-hash");
        when(passwordEncoder.matches("123456", "dummy-hash")).thenReturn(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyLoginPhoneCode("person@example.com", "123456")
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        assertTrue(staleCode.getUsed());
        assertNull(staleCode.getCode());
        assertNull(staleCode.getCodeHash());
        verify(verificationCodeRepository).save(staleCode);
        verify(passwordEncoder, never()).matches("123456", "otp-hash");
    }
}
