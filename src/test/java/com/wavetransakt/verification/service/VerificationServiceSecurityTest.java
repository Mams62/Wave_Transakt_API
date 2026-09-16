package com.wavetransakt.verification.service;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.verification.repository.VerificationCodeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VerificationServiceSecurityTest {

    @Mock
    VerificationCodeRepository verificationCodeRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void newlyIssuedCodeIsHashedAndPlaintextIsNeverPersisted() {
        User user = User.builder().email("person@example.com").build();
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, VerificationType.EMAIL))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(any(String.class))).thenReturn("bcrypt-hash");
        when(verificationCodeRepository.save(any(VerificationCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        VerificationService service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );

        String rawCode = service.createEmailVerificationCode(user);

        assertTrue(rawCode.matches("\\d{6}"));

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(verificationCodeRepository).save(captor.capture());
        VerificationCode persisted = captor.getValue();

        assertNull(persisted.getCode());
        assertEquals("bcrypt-hash", persisted.getCodeHash());
        assertFalse(Boolean.TRUE.equals(persisted.getUsed()));
        verify(passwordEncoder).encode(rawCode);
    }

    @Test
    void hashedCodeVerifiesAndSecretIsScrubbedAfterUse() {
        User user = User.builder()
                .email("person@example.com")
                .emailVerified(false)
                .accountStatus(AccountStatus.PENDING)
                .build();
        VerificationCode stored = activeCode(user, null, "bcrypt-hash");

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, VerificationType.EMAIL))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("123456", "bcrypt-hash")).thenReturn(true);

        VerificationService service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );

        service.verifyEmail("Person@Example.com", "123456");

        assertTrue(Boolean.TRUE.equals(stored.getUsed()));
        assertNull(stored.getCode());
        assertNull(stored.getCodeHash());
        assertTrue(Boolean.TRUE.equals(user.getEmailVerified()));
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        verify(verificationCodeRepository).save(stored);
        verify(userRepository).save(user);
    }

    @Test
    void activeLegacyPlaintextCodeWorksOnceThenIsScrubbed() {
        User user = User.builder()
                .email("legacy@example.com")
                .emailVerified(false)
                .accountStatus(AccountStatus.PENDING)
                .build();
        VerificationCode stored = activeCode(user, "654321", null);

        when(userRepository.findByEmail("legacy@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, VerificationType.EMAIL))
                .thenReturn(Optional.of(stored));

        VerificationService service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );

        service.verifyEmail("legacy@example.com", "654321");

        assertTrue(Boolean.TRUE.equals(stored.getUsed()));
        assertNull(stored.getCode());
        assertNull(stored.getCodeHash());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void invalidHashedCodeDoesNotConsumeOrScrubTheActiveCode() {
        User user = User.builder().email("person@example.com").build();
        VerificationCode stored = activeCode(user, null, "bcrypt-hash");

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, VerificationType.EMAIL))
                .thenReturn(Optional.of(stored));
        when(passwordEncoder.matches("000000", "bcrypt-hash")).thenReturn(false);

        VerificationService service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("person@example.com", "000000")
        );

        assertFalse(Boolean.TRUE.equals(stored.getUsed()));
        assertEquals("bcrypt-hash", stored.getCodeHash());
        verify(verificationCodeRepository, never()).save(stored);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void expiredCodeIsRetiredAndSecretIsScrubbed() {
        User user = User.builder().email("person@example.com").build();
        VerificationCode stored = VerificationCode.builder()
                .user(user)
                .codeHash("bcrypt-hash")
                .type(VerificationType.EMAIL)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .used(false)
                .build();

        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(user));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(user, VerificationType.EMAIL))
                .thenReturn(Optional.of(stored));

        VerificationService service = new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("person@example.com", "123456")
        );

        assertTrue(exception.getMessage().toLowerCase().contains("expired"));
        assertTrue(Boolean.TRUE.equals(stored.getUsed()));
        assertNull(stored.getCode());
        assertNull(stored.getCodeHash());
        verify(verificationCodeRepository).save(stored);
        verifyNoInteractions(passwordEncoder);
    }

    private VerificationCode activeCode(User user, String legacyCode, String codeHash) {
        return VerificationCode.builder()
                .user(user)
                .code(legacyCode)
                .codeHash(codeHash)
                .type(VerificationType.EMAIL)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();
    }
}
