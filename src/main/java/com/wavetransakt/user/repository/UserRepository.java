package com.wavetransakt.user.repository;

import com.wavetransakt.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhone(String phone);

    /**
     * Serializes security-sensitive authentication-epoch changes so concurrent
     * credential reset / revoke-all operations cannot lose an increment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByBvn(String bvn);

    boolean existsByNin(String nin);
}