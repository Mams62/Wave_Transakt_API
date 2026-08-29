package com.wavetransakt.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_email", columnNames = "email")
})
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true, length = 20)
    private String walletNumber;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getWalletNumber() { return walletNumber; }
    public void setWalletNumber(String walletNumber) { this.walletNumber = walletNumber; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Instant getCreatedAt() { return createdAt; }
}
