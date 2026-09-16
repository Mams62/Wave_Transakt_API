package com.wavetransakt.wallet.funding;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CustomerFundingAccountService {

    private final WalletRepository walletRepository;
    private final CustomerFundingAccountRepository fundingAccountRepository;
    private final List<CustomerFundingAccountProvider> providers;
    private final boolean provisioningEnabled;

    public CustomerFundingAccountService(
            WalletRepository walletRepository,
            CustomerFundingAccountRepository fundingAccountRepository,
            List<CustomerFundingAccountProvider> providers,
            @Value("${wave.customer-funding-account-provisioning-enabled:false}") boolean provisioningEnabled
    ) {
        this.walletRepository = walletRepository;
        this.fundingAccountRepository = fundingAccountRepository;
        this.providers = providers;
        this.provisioningEnabled = provisioningEnabled;
    }

    public boolean isProvisioningEnabled() {
        return provisioningEnabled;
    }

    public List<CustomerFundingAccount> listForUser(UUID userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        return fundingAccountRepository.findByWalletIdOrderByCreatedAtAsc(wallet.getId());
    }

    /**
     * Provisioning is globally fail-closed. No provider call can occur unless
     * the Wave feature flag is explicitly enabled and the selected provider
     * reports itself ready for this product.
     */
    public CustomerFundingAccount provision(UUID userId, String requestedProvider) {
        if (!provisioningEnabled) {
            throw new IllegalStateException("Customer funding account provisioning is disabled");
        }

        String providerCode = normalizeProviderCode(requestedProvider);

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found"));

        CustomerFundingAccount existing = fundingAccountRepository
                .findByWalletIdAndProviderCode(wallet.getId(), providerCode)
                .orElse(null);

        if (existing != null &&
                (existing.getStatus() == CustomerFundingAccountStatus.ACTIVE ||
                        existing.getStatus() == CustomerFundingAccountStatus.PROVISIONING)) {
            return existing;
        }

        CustomerFundingAccountProvider provider = providers.stream()
                .filter(candidate -> providerCode.equalsIgnoreCase(candidate.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Funding account provider is not configured"));

        CustomerFundingAccountProvider.ProvisioningReadiness readiness = provider.readiness();
        if (readiness == null || !readiness.ready()) {
            // Do not echo provider/internal configuration detail to the client.
            throw new IllegalStateException("Funding account provider is not ready");
        }

        CustomerFundingAccount account = existing != null
                ? existing
                : CustomerFundingAccount.builder()
                        .wallet(wallet)
                        .providerCode(providerCode)
                        .build();

        account.setStatus(CustomerFundingAccountStatus.PROVISIONING);
        account.setProviderMessage("Provisioning started");
        fundingAccountRepository.save(account);

        User user = wallet.getUser();
        if (user == null) {
            markFailed(account, "Wallet owner is unavailable");
            throw new IllegalStateException("Wallet owner is unavailable");
        }

        try {
            CustomerFundingAccountProvider.ProvisioningResult result = provider.provision(
                    new CustomerFundingAccountProvider.ProvisioningRequest(
                            user.getId(),
                            wallet.getId(),
                            user.getFullName(),
                            user.getEmail(),
                            user.getPhone(),
                            Boolean.TRUE.equals(user.getBvnVerified()),
                            Boolean.TRUE.equals(user.getNinVerified())
                    )
            );

            if (result == null || result.accountNumber() == null || result.accountNumber().isBlank()) {
                markFailed(account, "Provider did not return an account number");
                throw new IllegalStateException("Provider did not return an account number");
            }

            account.setProviderAccountReference(trimToNull(result.providerAccountReference()));
            account.setAccountNumber(result.accountNumber().trim());
            account.setBankCode(trimToNull(result.bankCode()));
            account.setBankName(trimToNull(result.bankName()));
            account.setAccountName(trimToNull(result.accountName()));
            account.setProviderMessage(trimToNull(result.message()));
            account.setStatus(CustomerFundingAccountStatus.ACTIVE);
            account.setActivatedAt(LocalDateTime.now());
            return fundingAccountRepository.save(account);
        } catch (RuntimeException exception) {
            if (account.getStatus() != CustomerFundingAccountStatus.FAILED) {
                markFailed(account, "Provider provisioning failed");
            }
            throw exception;
        }
    }

    private void markFailed(CustomerFundingAccount account, String message) {
        account.setStatus(CustomerFundingAccountStatus.FAILED);
        account.setProviderMessage(message);
        fundingAccountRepository.save(account);
    }

    private String normalizeProviderCode(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Funding account provider is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40) {
            throw new IllegalArgumentException("Invalid funding account provider");
        }
        return normalized;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }
}
