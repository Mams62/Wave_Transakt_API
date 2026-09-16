package com.wavetransakt.auth.repository;

import com.wavetransakt.auth.entity.FaceLoginChallenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface FaceLoginChallengeRepository extends JpaRepository<FaceLoginChallenge, UUID> {

    Optional<FaceLoginChallenge> findByTokenHash(String tokenHash);

    /**
     * Serializes state-changing use of a one-time face-login challenge across
     * concurrent requests and across multiple API instances.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select challenge from FaceLoginChallenge challenge where challenge.tokenHash = :tokenHash")
    Optional<FaceLoginChallenge> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);
}
