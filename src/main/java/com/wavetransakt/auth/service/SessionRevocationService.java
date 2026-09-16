package com.wavetransakt.auth.service;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final UserRepository userRepository;

    /**
     * Revokes every authentication artifact from the caller's current epoch.
     * JWTs, login OTPs and face-login challenges become stale immediately after
     * this transaction commits because each is bound to authVersion.
     */
    @Transactional
    public void revokeAll(UUID userId) {
        if (userId == null) {
            throw new IllegalArgumentException("User is required");
        }

        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new IllegalArgumentException("User account not found"));

        user.advanceAuthVersion();
        userRepository.save(user);
    }
}
