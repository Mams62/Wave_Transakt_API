package com.wavetransakt.verification.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wavetransakt.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "verification_codes",
        indexes = {
                @Index(name = "idx_verification_codes_user_id", columnList = "user_id"),
                @Index(name = "idx_verification_codes_expires_at", columnList = "expires_at")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_verification_codes_user")
    )
    private User user;

    /** Legacy plaintext field kept temporarily for pre-V117 active codes only. */
    @JsonIgnore
    @Column(name = "code", length = 10)
    private String code;

    /** One-way password-strength hash for all newly issued verification codes. */
    @JsonIgnore
    @Column(name = "code_hash", length = 100)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private VerificationType type;

    @JsonIgnore
    @Column(name = "auth_version", nullable = false)
    @Builder.Default
    private long authVersion = 0L;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "used", nullable = false)
    @Builder.Default
    private Boolean used = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (used == null) {
            used = false;
        }
    }
}
