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

    private static final String GENERIC_FAILURE =
            "Invalid or expired verification code";

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

        VerificationService service = service();
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

        VerificationService service = service();
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
    void activeLegacyPlaintextCodeWorksOnceThenIsScrubbedWithComparableHashWork() {
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
        stubDummyHash();

        VerificationService service = service();
        service.verifyEmail("legacy@example.com", "654321");

        assertTrue(Boolean.TRUE.equals(stored.getUsed()));
        assertNull(stored.getCode());
        assertNull(stored.getCodeHash());
        verify(passwordEncoder).matches("654321", "dummy-hash");
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

        VerificationService service = service();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("person@example.com", "000000")
        );

        assertEquals(GENERIC_FAILURE, exception.getMessage());
        assertFalse(Boolean.TRUE.equals(stored.getUsed()));
        assertEquals("bcrypt-hash", stored.getCodeHash());
        verify(verificationCodeRepository, never()).save(stored);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void expiredCodeIsRetiredScrubbedAndUsesGenericFailure() {
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
        stubDummyHash();

        VerificationService service = service();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("person@example.com", "123456")
        );

        assertEquals(GENERIC_FAILURE, exception.getMessage());
        assertTrue(Boolean.TRUE.equals(stored.getUsed()));
        assertNull(stored.getCode());
        assertNull(stored.getCodeHash());
        verify(verificationCodeRepository).save(stored);
        verify(passwordEncoder).matches("123456", "dummy-hash");
    }

    @Test
    void unknownAccountAndExistingAccountWithoutCodeReturnSameFailure() {
        User existing = User.builder().email("existing@example.com").build();

        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));
        when(verificationCodeRepository
                .findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(existing, VerificationType.EMAIL))
                .thenReturn(Optional.empty());
        stubDummyHash();

        VerificationService service = service();
        IllegalArgumentException missing = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("missing@example.com", "123456")
        );
        IllegalArgumentException noCode = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("existing@example.com", "123456")
        );

        assertEquals(GENERIC_FAILURE, missing.getMessage());
        assertEquals(missing.getMessage(), noCode.getMessage());
        verify(passwordEncoder, times(2)).matches("123456", "dummy-hash");
    }

    @Test
    void unknownLoginOtpIdentifierUsesGenericFailureAndDummyHashWork() {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("ghost@example.com")).thenReturn(Optional.empty());
        stubDummyHash();

        VerificationService service = service();
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyLoginPhoneCode("ghost@example.com", "432198")
        );

        assertEquals(GENERIC_FAILURE, exception.getMessage());
        verify(passwordEncoder).matches("432198", "dummy-hash");
        verifyNoInteractions(verificationCodeRepository);
    }

    @Test
    void resendUnknownAndAlreadyVerifiedAccountsReturnSameNullOutcome() {
        User verified = User.builder()
                .email("verified@example.com")
                .emailVerified(true)
                .build();
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("verified@example.com")).thenReturn(Optional.of(verified));
        stubDummyHash();

        VerificationService service = service();
        String missingResult = service.resendEmailVerification("missing@example.com");
        String verifiedResult = service.resendEmailVerification("verified@example.com");

        assertNull(missingResult);
        assertNull(verifiedResult);
        verify(passwordEncoder, times(2)).matches("000000", "dummy-hash");
        verifyNoInteractions(verificationCodeRepository);
    }

    @Test
    void malformedCodeIsRejectedBeforeAccountLookup() {
        VerificationService service = service();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyEmail("target@example.com", "12")
        );

        assertEquals("Verification code must be exactly 6 digits", exception.getMessage());
        verifyNoInteractions(userRepository, verificationCodeRepository, passwordEncoder);
    }

    private VerificationService service() {
        return new VerificationService(
                verificationCodeRepository,
                userRepository,
                passwordEncoder
        );
    }

    private void stubDummyHash() {
        when(passwordEncoder.encode("000000")).thenReturn("dummy-hash");
        when(passwordEncoder.matches(anyString(), eq("dummy-hash"))).thenReturn(false);
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
