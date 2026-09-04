package com.wavetransakt.verification.repository;

import com.wavetransakt.verification.entity.VerificationCode;
import com.wavetransakt.verification.entity.VerificationType;
import com.wavetransakt.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationCodeRepository
        extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findTopByUserAndTypeAndUsedFalseOrderByCreatedAtDesc(
            User user,
            VerificationType type
    );
}