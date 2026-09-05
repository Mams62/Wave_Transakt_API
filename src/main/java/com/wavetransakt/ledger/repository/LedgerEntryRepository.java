package com.wavetransakt.ledger.repository;

import com.wavetransakt.ledger.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface LedgerEntryRepository
        extends JpaRepository<LedgerEntry, UUID> {

    @Query(
            value = """
                    SELECT COALESCE(
                        SUM(
                            CASE
                                WHEN direction = 'CREDIT'
                                    THEN amount
                                ELSE -amount
                            END
                        ),
                        0
                    )
                    FROM ledger_entries
                    WHERE account_id = :accountId
                    """,
            nativeQuery = true
    )
    BigDecimal calculateAccountBalance(
            @Param("accountId")
            UUID accountId
    );
}