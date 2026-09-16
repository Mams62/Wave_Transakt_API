package com.wavetransakt.wallet.funding;

import java.util.UUID;

/**
 * Server-side provider boundary for persistent customer funding accounts.
 * Android/POS must never call a bank/provider account-provisioning API directly.
 */
public interface CustomerFundingAccountProvider {

    String code();

    ProvisioningReadiness readiness();

    ProvisioningResult provision(ProvisioningRequest request);

    record ProvisioningReadiness(
            boolean ready,
            String message
    ) {
    }

    record ProvisioningRequest(
            UUID userId,
            UUID walletId,
            String fullName,
            String email,
            String phone,
            boolean bvnVerified,
            boolean ninVerified
    ) {
    }

    record ProvisioningResult(
            String providerAccountReference,
            String accountNumber,
            String bankCode,
            String bankName,
            String accountName,
            String message
    ) {
    }
}
