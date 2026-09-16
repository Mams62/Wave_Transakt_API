package com.wavetransakt.auth.repository;

import com.wavetransakt.auth.entity.PasswordResetToken;
import com.wavetransakt.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    /**
     * Serializes consumption of a recovery token so two concurrent reset
     * requests cannot both observe the same credential as unused.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PasswordResetToken> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    void deleteByUser(User user);
}
