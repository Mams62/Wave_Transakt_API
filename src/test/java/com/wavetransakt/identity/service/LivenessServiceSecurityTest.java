package com.wavetransakt.identity.service;

import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.provider.DojahLivenessClient;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LivenessServiceSecurityTest {

    @Mock LivenessSessionRepository repository;
    @Mock DojahLivenessClient dojahLivenessClient;
    @Mock UserRepository userRepository;

    @Test
    void captureLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = LivenessSessionRepository.class
                .getMethod("findByIdForUpdate", UUID.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void oversizedSelfieIsRejectedBeforeDatabaseOrProviderWork() {
        User user = user();
        String oversized = "A".repeat(LivenessService.MAX_SELFIE_BASE64_CHARS + 1);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(true).capture(user, UUID.randomUUID(), oversized)
        );

        assertEquals("Selfie image is too large", error.getMessage());
        verifyNoInteractions(repository, dojahLivenessClient, userRepository);
    }

    @Test
    void malformedBase64IsRejectedBeforeDatabaseOrProviderWork() {
        User user = user();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> service(true).capture(user, UUID.randomUUID(), "not-base64!!")
        );

        assertEquals("Selfie image must be valid Base64", error.getMessage());
        verifyNoInteractions(repository, dojahLivenessClient, userRepository);
    }

    @Test
    void inProgressSessionCannotIssueAnotherProviderRequest() {
        User user = user();
        UUID sessionId = UUID.randomUUID();
        LivenessSession session = session(user, sessionId, LivenessStatus.IN_PROGRESS);
        String selfie = Base64.getEncoder().encodeToString("selfie".getBytes(StandardCharsets.UTF_8));

        when(repository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(dojahLivenessClient.isConfigured()).thenReturn(true);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service(true).capture(user, sessionId, selfie)
        );

        assertEquals("Liveness verification is already in progress", error.getMessage());
        verify(repository).findByIdForUpdate(sessionId);
        verify(dojahLivenessClient, never()).check(anyString());
        verify(dojahLivenessClient, never()).verifySelfieNin(anyString(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void validDataUrlUsesLockedSessionAndSendsNormalizedBase64ToProvider() {
        User user = user();
        UUID sessionId = UUID.randomUUID();
        LivenessSession session = session(user, sessionId, LivenessStatus.PENDING);
        String encoded = Base64.getEncoder().encodeToString("selfie".getBytes(StandardCharsets.UTF_8));
        String dataUrl = "data:image/jpeg;base64," + encoded;

        when(repository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(dojahLivenessClient.isConfigured()).thenReturn(true);
        when(dojahLivenessClient.check(encoded))
                .thenReturn(new DojahLivenessClient.Result(true, 99.0, true, false));
        when(dojahLivenessClient.verifySelfieNin("12345678901", encoded))
                .thenReturn(new DojahLivenessClient.IdentityMatchResult(true, 98.0));

        var response = service(true).capture(user, sessionId, dataUrl);

        assertEquals("VERIFIED", response.status());
        assertEquals(LivenessStatus.VERIFIED, session.getStatus());
        assertNotNull(session.getVerifiedAt());
        verify(repository).findByIdForUpdate(sessionId);
        verify(repository, never()).findById(sessionId);
        verify(dojahLivenessClient).check(encoded);
        verify(dojahLivenessClient).verifySelfieNin("12345678901", encoded);
    }

    private LivenessService service(boolean providerConfigured) {
        LivenessService service = new LivenessService(
                repository,
                dojahLivenessClient,
                userRepository
        );
        ReflectionTestUtils.setField(service, "providerConfigured", providerConfigured);
        ReflectionTestUtils.setField(service, "provider", "DOJAH");
        return service;
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .nin("12345678901")
                .build();
    }

    private LivenessSession session(User user, UUID sessionId, LivenessStatus status) {
        return LivenessSession.builder()
                .id(sessionId)
                .user(user)
                .provider("DOJAH")
                .purpose("LOGIN")
                .status(status)
                .build();
    }
}
