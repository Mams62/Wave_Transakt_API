package com.wavetransakt.auth.repository;

import com.wavetransakt.auth.entity.FaceLoginChallenge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FaceLoginChallengeRepository extends JpaRepository<FaceLoginChallenge, UUID> {
    Optional<FaceLoginChallenge> findByTokenHash(String tokenHash);
}
