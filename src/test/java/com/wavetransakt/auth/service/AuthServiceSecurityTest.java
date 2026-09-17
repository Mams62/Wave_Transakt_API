package com.wavetransakt.auth.service;

import com.wavetransakt.auth.dto.LoginOtpRequest;
import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import com.wavetransakt.security.JwtService;
import com.wavetransakt.security.ratelimit.RateLimitExceededException;
import com.wavetransakt.security.ratelimit.RateLimitGuard;
import com.wavetransakt.user.dto.LoginRequest;
import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import com.wavetransakt.verification.service.SmsOtpSender;
import com.wavetransakt.verification.service.VerificationService;
import com.wavetransakt.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceSecurityTest {

    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock WalletService walletService;
    @Mock VerificationService verificationService;
    @Mock SmsOtpSender smsOtpSender;
    @Mock FaceLoginChallengeService faceLoginChallengeService;
    @Mock DojahGovernmentIdentityClient governmentIdentityClient;
    @Mock JwtService jwtService;
    @Mock RateLimitGuard rateLimitGuard;

    @Test
    void missingAccountPerformsPasswordWorkCountsFailureAndReturnsGenericCredentialError() {
        LoginRequest request = loginRequest("missing@example.com", "123456");
        when(rateLimitGuard.canonicalIdentifier("missing@example.com")).thenReturn("missing@example.com");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("discarded-bcrypt-hash");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().login(request)
        );

        assertEquals("Invalid email/phone or account PIN", error.getMessage());
        verify(rateLimitGuard).requireNotBlocked(
                "AUTH_LOGIN_FAILURE",
                "missing@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard).requireAllowed(
                "AUTH_LOGIN_FAILURE",
                "missing@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(passwordEncoder).encode(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verifyNoInteractions(smsOtpSender);
    }

    @Test
    void wrongPinUsesSamePublicErrorAndCountsFailure() {
        User existing = activeUser();
        LoginRequest wrongPin = loginRequest("person@example.com", "654321");
        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("654321", "stored-bcrypt-hash")).thenReturn(false);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().login(wrongPin)
        );

        assertEquals("Invalid email/phone or account PIN", error.getMessage());
        verify(rateLimitGuard).requireNotBlocked(
                "AUTH_LOGIN_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard).requireAllowed(
                "AUTH_LOGIN_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(passwordEncoder).matches("654321", "stored-bcrypt-hash");
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void successfulLoginChecksFailureLockButDoesNotConsumeFailureQuota() {
        User existing = activeUser();
        LoginRequest request = loginRequest("person@example.com", "123456");
        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("123456", "stored-bcrypt-hash")).thenReturn(true);
        when(verificationService.createPhoneVerificationCode(existing)).thenReturn("111222");
        when(smsOtpSender.sendLoginOtp("08000000000", "111222")).thenReturn(true);

        service().login(request);

        verify(rateLimitGuard).requireNotBlocked(
                "AUTH_LOGIN_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard, never()).requireAllowed(
                eq("AUTH_LOGIN_FAILURE"),
                anyString(),
                eq(8),
                eq(Duration.ofMinutes(10))
        );
        verify(verificationService).createPhoneVerificationCode(existing);
        verify(smsOtpSender).sendLoginOtp("08000000000", "111222");
    }

    @Test
    void lockedIdentifierFailsBeforeAccountLookupOrPasswordWork() {
        LoginRequest request = loginRequest("person@example.com", "123456");
        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        doThrow(new RateLimitExceededException(120))
                .when(rateLimitGuard)
                .requireNotBlocked(
                        "AUTH_LOGIN_FAILURE",
                        "person@example.com",
                        8,
                        Duration.ofMinutes(10)
                );

        assertThrows(RateLimitExceededException.class, () -> service().login(request));

        verifyNoInteractions(userRepository, passwordEncoder, verificationService, smsOtpSender);
        verify(rateLimitGuard, never()).requireAllowed(anyString(), anyString(), anyInt(), any(Duration.class));
    }

    @Test
    void invalidLoginOtpCountsFailure() {
        LoginOtpRequest request = loginOtpRequest("person@example.com", "000000");
        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        when(verificationService.verifyLoginPhoneCode("person@example.com", "000000"))
                .thenThrow(new IllegalArgumentException("Invalid or expired verification code"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service().verifyLoginOtp(request)
        );

        assertEquals("Invalid or expired verification code", error.getMessage());
        verify(rateLimitGuard).requireNotBlocked(
                "AUTH_LOGIN_OTP_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard).requireAllowed(
                "AUTH_LOGIN_OTP_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
    }

    @Test
    void successfulLoginOtpChecksLockButDoesNotConsumeFailureQuota() {
        LoginOtpRequest request = loginOtpRequest("person@example.com", "111222");
        User existing = activeUser();
        existing.setNinVerified(true);
        FaceLoginChallengeService.IssuedChallenge challenge =
                new FaceLoginChallengeService.IssuedChallenge(
                        "face-token",
                        LocalDateTime.now().plusMinutes(10)
                );

        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        when(verificationService.verifyLoginPhoneCode("person@example.com", "111222"))
                .thenReturn(existing);
        when(faceLoginChallengeService.issue(existing)).thenReturn(challenge);

        service().verifyLoginOtp(request);

        verify(rateLimitGuard).requireNotBlocked(
                "AUTH_LOGIN_OTP_FAILURE",
                "person@example.com",
                8,
                Duration.ofMinutes(10)
        );
        verify(rateLimitGuard, never()).requireAllowed(
                eq("AUTH_LOGIN_OTP_FAILURE"),
                anyString(),
                eq(8),
                eq(Duration.ofMinutes(10))
        );
        verify(faceLoginChallengeService).issue(existing);
    }

    @Test
    void lockedOtpIdentifierFailsBeforeOtpVerification() {
        LoginOtpRequest request = loginOtpRequest("person@example.com", "111222");
        when(rateLimitGuard.canonicalIdentifier("person@example.com")).thenReturn("person@example.com");
        doThrow(new RateLimitExceededException(120))
                .when(rateLimitGuard)
                .requireNotBlocked(
                        "AUTH_LOGIN_OTP_FAILURE",
                        "person@example.com",
                        8,
                        Duration.ofMinutes(10)
                );

        assertThrows(RateLimitExceededException.class, () -> service().verifyLoginOtp(request));

        verifyNoInteractions(verificationService, faceLoginChallengeService);
        verify(rateLimitGuard, never()).requireAllowed(
                eq("AUTH_LOGIN_OTP_FAILURE"),
                anyString(),
                anyInt(),
                any(Duration.class)
        );
    }

    private AuthService service() {
        return new AuthService(
                userRepository,
                passwordEncoder,
                walletService,
                verificationService,
                smsOtpSender,
                faceLoginChallengeService,
                governmentIdentityClient,
                jwtService,
                rateLimitGuard
        );
    }

    private User activeUser() {
        return User.builder()
                .email("person@example.com")
                .phone("08000000000")
                .password("stored-bcrypt-hash")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private LoginRequest loginRequest(String identifier, String pin) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setAccountPin(pin);
        return request;
    }

    private LoginOtpRequest loginOtpRequest(String identifier, String otp) {
        LoginOtpRequest request = new LoginOtpRequest();
        request.setIdentifier(identifier);
        request.setOtp(otp);
        return request;
    }
}
