package com.wavetransakt.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
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

    @Column(unique = true, length = 20)
    private String phone;

    @Column(length = 80)
    private String state;

    @Column(length = 100)
    private String localGovernment;

    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String gender;

    @Column(nullable = false, unique = true, length = 20)
    private String walletNumber;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(length = 100)
    private String transactionPinHash;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getState() { return state; }
    public void setState(String state) { this.state = state; }
    public String getLocalGovernment() { return localGovernment; }
    public void setLocalGovernment(String localGovernment) { this.localGovernment = localGovernment; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public String getWalletNumber() { return walletNumber; }
    public void setWalletNumber(String walletNumber) { this.walletNumber = walletNumber; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getTransactionPinHash() { return transactionPinHash; }
    public void setTransactionPinHash(String transactionPinHash) { this.transactionPinHash = transactionPinHash; }
    public Instant getCreatedAt() { return createdAt; }
}
