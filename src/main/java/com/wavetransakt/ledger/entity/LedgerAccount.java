package com.wavetransakt.ledger.entity;

import com.wavetransakt.wallet.entity.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(
            nullable = false,
            unique = true,
            length = 100
    )
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "account_type",
            nullable = false,
            length = 20
    )
    private LedgerAccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "account_class",
            nullable = false,
            length = 20
    )
    private LedgerAccountClass accountClass;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "wallet_id",
            unique = true
    )
    private Wallet wallet;

    @Column(
            nullable = false,
            length = 3
    )
    private String currency;

    @Column(
            name = "created_at",
            nullable = false
    )
    @Builder.Default
    private LocalDateTime createdAt =
            LocalDateTime.now();
}