package com.wavetransakt.user.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "users",
        indexes = {
                @Index(name = "idx_users_email", columnList = "email"),
                @Index(name = "idx_users_phone", columnList = "phone"),
                @Index(name = "idx_users_bvn", columnList = "bvn"),
                @Index(name = "idx_users_nin", columnList = "nin")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, unique = true, length = 20)
    private String phone;

    @JsonIgnore
    @Column(nullable = false)
    private String password;

    @JsonIgnore
    @Column(unique = true, length = 20)
    private String bvn;

    @JsonIgnore
    @Column(unique = true, length = 20)
    private String nin;

    @Column(length = 80)
    private String state;

    @Column(name = "local_government", length = 100)
    private String localGovernment;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(length = 30)
    private String gender;

    @JsonIgnore
    @Column(name = "transaction_pin_hash", length = 100)
    private String transactionPinHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private UserRole role = UserRole.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private AccountStatus accountStatus = AccountStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(nullable = false)
    @Builder.Default
    private Boolean phoneVerified = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @OneToOne(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private Wallet wallet;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();

        if (createdAt == null) {
            createdAt = now;
        }

        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isEnabled() {
        return accountStatus == AccountStatus.ACTIVE;
    }

    public String getFullName() {
        return (firstName + " " + lastName).trim();
    }
}
