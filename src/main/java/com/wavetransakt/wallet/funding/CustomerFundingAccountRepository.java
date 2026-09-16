package com.wavetransakt.wallet.funding;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerFundingAccountRepository extends JpaRepository<CustomerFundingAccount, UUID> {

    List<CustomerFundingAccount> findByWalletIdOrderByCreatedAtAsc(UUID walletId);

    Optional<CustomerFundingAccount> findByWalletIdAndProviderCode(UUID walletId, String providerCode);
}
