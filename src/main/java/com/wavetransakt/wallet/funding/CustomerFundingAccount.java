package com.wavetransakt.wallet.funding;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "customer_funding_accounts",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_customer_funding_account_wallet_provider", columnNames = {"wallet_id", "provider_code"})
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFundingAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Column(name = "provider_code", nullable = false, length = 40)
    private String providerCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private CustomerFundingAccountStatus status = CustomerFundingAccountStatus.NOT_REQUESTED;

    @Column(name = "provider_account_reference", length = 120)
    private String providerAccountReference;

    @Column(name = "account_number", length = 32)
    private String accountNumber;

    @Column(name = "bank_code", length = 20)
    private String bankCode;

    @Column(name = "bank_name", length = 120)
    private String bankName;

    @Column(name = "account_name", length = 180)
    private String accountName;

    @Column(name = "provider_message", length = 255)
    private String providerMessage;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
