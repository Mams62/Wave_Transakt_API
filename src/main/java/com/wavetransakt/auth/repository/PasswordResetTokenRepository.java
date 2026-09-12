package com.wavetransakt.auth.repository;

import com.wavetransakt.auth.entity.PasswordResetToken;
import com.wavetransakt.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    Optional<PasswordResetToken> findTopByUserAndUsedFalseOrderByCreatedAtDesc(User user);

    void deleteByUser(User user);
}
