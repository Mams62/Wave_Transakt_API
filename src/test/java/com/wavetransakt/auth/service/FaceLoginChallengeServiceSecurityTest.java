package com.wavetransakt.auth.service;

import com.wavetransakt.auth.entity.FaceLoginChallenge;
import com.wavetransakt.auth.repository.FaceLoginChallengeRepository;
import com.wavetransakt.identity.dto.LivenessSessionResponse;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
import com.wavetransakt.identity.service.LivenessService;
import com.wavetransakt.security.JwtService;
import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaceLoginChallengeServiceSecurityTest {

    @Mock FaceLoginChallengeRepository repository;
    @Mock LivenessSessionRepository livenessSessionRepository;
    @Mock JwtService jwtService;
    @Mock LivenessService livenessService;

    @Test
    void stateChangingLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = FaceLoginChallengeRepository.class
                .getMethod("findByTokenHashForUpdate", String.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
    }

    @Test
    void startCreatesAndBindsExactlyOneLivenessSessionUnderChallengeLock() {
        User user = activeUser();
        FaceLoginChallenge challenge = activeChallenge(user, null);
        UUID sessionId = UUID.randomUUID();
        LivenessSessionResponse response = livenessResponse(sessionId);

        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));
        when(livenessService.start(user, "LOGIN")).thenReturn(response);
        when(repository.save(challenge)).thenReturn(challenge);

        LivenessSessionResponse result = service().startOrGetLivenessSession("challenge-token");

        assertSame(response, result);
        assertEquals(sessionId, challenge.getLivenessSessionId());
        verify(repository).findByTokenHashForUpdate(anyString());
        verify(livenessService).start(user, "LOGIN");
        verify(repository).save(challenge);
        verify(livenessService, never()).status(any(), any());
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    void repeatedStartReturnsBoundSessionWithoutCreatingAnother() {
        User user = activeUser();
        UUID sessionId = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(user, sessionId);
        LivenessSessionResponse response = livenessResponse(sessionId);

        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));
        when(livenessService.status(user, sessionId)).thenReturn(response);

        LivenessSessionResponse result = service().startOrGetLivenessSession("challenge-token");

        assertSame(response, result);
        verify(livenessService).status(user, sessionId);
        verify(livenessService, never()).start(any(), anyString());
        verify(repository, never()).save(any());
    }

    @Test
    void bindingUsesLockedLookupAndCannotRebindToAnotherSession() {
        UUID existingSession = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(activeUser(), existingSession);
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));

        FaceLoginChallengeService service = service();

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.bindLivenessSession("challenge-token", UUID.randomUUID())
        );

        assertTrue(error.getMessage().contains("already bound"));
        verify(repository).findByTokenHashForUpdate(anyString());
        verify(repository, never()).save(any());
        verify(repository, never()).findByTokenHash(anyString());
    }

    @Test
    void bindingSameSessionIsIdempotent() {
        UUID sessionId = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(activeUser(), sessionId);
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));

        FaceLoginChallenge result = service().bindLivenessSession("challenge-token", sessionId);

        assertSame(challenge, result);
        verify(repository, never()).save(any());
    }

    @Test
    void completionConsumesLockedChallengeBeforeJwtIsIssued() {
        User user = activeUser();
        user.setAuthVersion(4L);
        UUID sessionId = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(user, sessionId);
        LivenessSession session = LivenessSession.builder()
                .id(sessionId)
                .user(user)
                .purpose("LOGIN")
                .status(LivenessStatus.VERIFIED)
                .verifiedAt(LocalDateTime.now())
                .build();

        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));
        when(livenessSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(repository.save(challenge)).thenReturn(challenge);
        when(jwtService.generateToken(user.getId(), user.getEmail(), 4L)).thenReturn("jwt-token");

        String token = service().complete("challenge-token", sessionId);

        assertEquals("jwt-token", token);
        assertNotNull(challenge.getConsumedAt());
        verify(repository, never()).findByTokenHash(anyString());

        InOrder inOrder = inOrder(repository, jwtService);
        inOrder.verify(repository).findByTokenHashForUpdate(anyString());
        inOrder.verify(repository).save(challenge);
        inOrder.verify(jwtService).generateToken(user.getId(), user.getEmail(), 4L);
    }

    @Test
    void staleChallengeCannotReachLivenessOrJwtAfterCredentialReset() {
        User user = activeUser();
        user.setAuthVersion(2L);
        UUID sessionId = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(user, sessionId);
        challenge.setAuthVersion(1L);
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service().complete("challenge-token", sessionId)
        );

        assertTrue(error.getMessage().contains("no longer valid"));
        verifyNoInteractions(livenessSessionRepository, jwtService);
        verify(repository, never()).save(any());
    }

    @Test
    void consumedChallengeCannotIssueAnotherJwt() {
        User user = activeUser();
        UUID sessionId = UUID.randomUUID();
        FaceLoginChallenge challenge = activeChallenge(user, sessionId);
        challenge.setConsumedAt(LocalDateTime.now().minusSeconds(1));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(challenge));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service().complete("challenge-token", sessionId)
        );

        assertTrue(error.getMessage().contains("already used"));
        verifyNoInteractions(livenessSessionRepository, jwtService);
        verify(repository, never()).save(any());
    }

    private FaceLoginChallengeService service() {
        return new FaceLoginChallengeService(
                repository,
                livenessSessionRepository,
                jwtService,
                livenessService
        );
    }

    private LivenessSessionResponse livenessResponse(UUID sessionId) {
        return new LivenessSessionResponse(
                sessionId,
                "DOJAH",
                null,
                "PENDING",
                "LOGIN",
                "Capture a live selfie.",
                true,
                LocalDateTime.now(),
                null
        );
    }

    private User activeUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("person@example.com")
                .phone("08000000000")
                .password("hash")
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    private FaceLoginChallenge activeChallenge(User user, UUID livenessSessionId) {
        return FaceLoginChallenge.builder()
                .id(UUID.randomUUID())
                .user(user)
                .tokenHash("a".repeat(64))
                .livenessSessionId(livenessSessionId)
                .authVersion(user.getAuthVersion())
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
