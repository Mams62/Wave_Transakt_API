package com.wavetransakt.auth.service;

import com.wavetransakt.auth.entity.FaceLoginChallenge;
import com.wavetransakt.auth.repository.FaceLoginChallengeRepository;
import com.wavetransakt.identity.entity.LivenessSession;
import com.wavetransakt.identity.entity.LivenessStatus;
import com.wavetransakt.identity.repository.LivenessSessionRepository;
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

    @Test
    void stateChangingLookupUsesPessimisticWriteLock() throws Exception {
        Lock lock = FaceLoginChallengeRepository.class
                .getMethod("findByTokenHashForUpdate", String.class)
                .getAnnotation(Lock.class);

        assertNotNull(lock);
        assertEquals(LockModeType.PESSIMISTIC_WRITE, lock.value());
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
        when(jwtService.generateToken(user.getId(), user.getEmail())).thenReturn("jwt-token");

        String token = service().complete("challenge-token", sessionId);

        assertEquals("jwt-token", token);
        assertNotNull(challenge.getConsumedAt());
        verify(repository, never()).findByTokenHash(anyString());

        InOrder inOrder = inOrder(repository, jwtService);
        inOrder.verify(repository).findByTokenHashForUpdate(anyString());
        inOrder.verify(repository).save(challenge);
        inOrder.verify(jwtService).generateToken(user.getId(), user.getEmail());
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
        return new FaceLoginChallengeService(repository, livenessSessionRepository, jwtService);
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
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .createdAt(LocalDateTime.now())
                .build();
    }
}
