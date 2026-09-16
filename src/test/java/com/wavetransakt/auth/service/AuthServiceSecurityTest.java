package com.wavetransakt.auth.service;

import com.wavetransakt.identity.provider.DojahGovernmentIdentityClient;
import com.wavetransakt.security.JwtService;
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

    @Test
    void missingAccountPerformsPasswordWorkAndReturnsGenericCredentialError() {
        LoginRequest request = loginRequest("missing@example.com", "123456");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("discarded-bcrypt-hash");

        AuthService service = service();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(request)
        );

        assertEquals("Invalid email/phone or account PIN", error.getMessage());
        verify(passwordEncoder).encode(anyString());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verifyNoInteractions(smsOtpSender);
    }

    @Test
    void wrongPinUsesSamePublicErrorAsMissingAccount() {
        LoginRequest missing = loginRequest("missing@example.com", "123456");
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByPhone("missing@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("discarded-bcrypt-hash");

        AuthService service = service();
        IllegalArgumentException missingError = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(missing)
        );

        reset(userRepository, passwordEncoder);

        User existing = User.builder()
                .email("person@example.com")
                .phone("08000000000")
                .password("stored-bcrypt-hash")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        LoginRequest wrongPin = loginRequest("person@example.com", "654321");
        when(userRepository.findByEmail("person@example.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.matches("654321", "stored-bcrypt-hash")).thenReturn(false);

        IllegalArgumentException wrongPinError = assertThrows(
                IllegalArgumentException.class,
                () -> service.login(wrongPin)
        );

        assertEquals(missingError.getMessage(), wrongPinError.getMessage());
        verify(passwordEncoder).matches("654321", "stored-bcrypt-hash");
        verify(passwordEncoder, never()).encode(anyString());
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
                jwtService
        );
    }

    private LoginRequest loginRequest(String identifier, String pin) {
        LoginRequest request = new LoginRequest();
        request.setIdentifier(identifier);
        request.setAccountPin(pin);
        return request;
    }
}
