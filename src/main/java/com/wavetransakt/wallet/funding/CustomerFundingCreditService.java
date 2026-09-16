package com.wavetransakt.wallet.funding;

import com.wavetransakt.ledger.service.ProviderFundingLedgerService;
import com.wavetransakt.wallet.entity.Wallet;
import com.wavetransakt.wallet.entity.WalletStatus;
import com.wavetransakt.wallet.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Final gate between VERIFIED provider funding and spendable Wave balance.
 *
 * Crediting requires all of the following:
 * 1. global Wave credit switch enabled;
 * 2. event is VERIFIED and mapped to an ACTIVE funding account;
 * 3. one exact provider finality policy exists and returns ALLOW;
 * 4. an audited provider settlement asset ledger account is configured;
 * 5. wallet/ledger update succeeds atomically.
 *
 * There are intentionally no provider-specific ALLOW policies in the base
 * application. Therefore this service is deny-by-default even if the global
 * switch is accidentally enabled.
 */
@Service
public class CustomerFundingCreditService {

    private final CustomerFundingEventRepository eventRepository;
    private final WalletRepository walletRepository;
    private final ProviderFundingLedgerService providerFundingLedgerService;
    private final List<FundingCreditPolicy> policies;
    private final boolean creditEnabled;

    public CustomerFundingCreditService(
            CustomerFundingEventRepository eventRepository,
            WalletRepository walletRepository,
            ProviderFundingLedgerService providerFundingLedgerService,
            List<FundingCreditPolicy> policies,
            @Value("${wave.customer-funding-credit-enabled:false}") boolean creditEnabled
    ) {
        this.eventRepository = eventRepository;
        this.walletRepository = walletRepository;
        this.providerFundingLedgerService = providerFundingLedgerService;
        this.policies = policies;
        this.creditEnabled = creditEnabled;
    }

    public boolean isCreditEnabled() {
        return creditEnabled;
    }

    @Transactional
    public CreditResult attemptCredit(UUID eventId) {
        if (!creditEnabled) {
            throw new IllegalStateException("Customer funding credit is disabled");
        }

        if (eventId == null) {
            throw new IllegalArgumentException("Funding event ID is required");
        }

        CustomerFundingEvent event = eventRepository.findByIdForUpdate(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Funding event not found"));

        if (event.getStatus() == CustomerFundingEventStatus.CREDITED) {
            return result(event, true, true);
        }

        if (event.getStatus() != CustomerFundingEventStatus.VERIFIED) {
            throw new IllegalStateException("Funding event is not verified for credit assessment");
        }

        validateFinancialFields(event);

        CustomerFundingAccount fundingAccount = event.getFundingAccount();
        if (fundingAccount == null || fundingAccount.getStatus() != CustomerFundingAccountStatus.ACTIVE) {
            return reconciliation(
                    event,
                    "UNCONFIGURED",
                    "Funding account is unavailable or inactive"
            );
        }

        String providerCode = normalizeProvider(event.getProviderCode());
        FundingCreditPolicy policy = findExactPolicy(providerCode);
        if (policy == null) {
            return reconciliation(
                    event,
                    "UNCONFIGURED",
                    "No approved provider finality policy is configured"
            );
        }

        String policyCode = normalizePolicyCode(policy.policyCode());
        FundingCreditPolicy.CreditDecision decision = policy.evaluate(event);
        if (decision == null || decision.outcome() == null) {
            return reconciliation(
                    event,
                    policyCode,
                    "Provider finality policy returned no decision"
            );
        }

        String safeReason = normalizeReason(decision.reason());
        event.setCreditPolicyCode(policyCode);
        event.setCreditDecision(decision.outcome().name());
        event.setCreditDecisionReason(safeReason);
        event.setCreditDecidedAt(LocalDateTime.now());

        if (decision.outcome() == FundingCreditPolicy.Outcome.RECONCILIATION_REQUIRED) {
            event.setStatus(CustomerFundingEventStatus.RECONCILIATION_REQUIRED);
            eventRepository.save(event);
            return result(event, false, false);
        }

        if (decision.outcome() == FundingCreditPolicy.Outcome.DENY) {
            event.setStatus(CustomerFundingEventStatus.REJECTED);
            eventRepository.save(event);
            return result(event, false, false);
        }

        if (!providerFundingLedgerService.hasSettlementAccount(providerCode, event.getCurrency())) {
            return reconciliation(
                    event,
                    policyCode,
                    "Provider settlement ledger account is not configured"
            );
        }

        Wallet accountWallet = fundingAccount.getWallet();
        if (accountWallet == null || accountWallet.getId() == null) {
            return reconciliation(
                    event,
                    policyCode,
                    "Funding account is not linked to a Wave wallet"
            );
        }

        Wallet wallet = walletRepository.findByIdForUpdate(accountWallet.getId())
                .orElseThrow(() -> new IllegalStateException("Wave wallet is unavailable"));

        if (wallet.getStatus() != WalletStatus.ACTIVE) {
            throw new IllegalStateException("Wave wallet is not active");
        }
        if (wallet.getBalance() == null) {
            throw new IllegalStateException("Wave wallet balance is unavailable");
        }
        if (wallet.getCurrency() == null || !wallet.getCurrency().equalsIgnoreCase(event.getCurrency())) {
            throw new IllegalArgumentException("Wave wallet currency mismatch");
        }

        String ledgerReference = "PROVIDER_FUNDING:" + event.getId();

        providerFundingLedgerService.recordProviderFunding(
                ledgerReference,
                providerCode,
                wallet,
                event.getAmount(),
                event.getCurrency(),
                "Provider funding " + providerCode + " event " + event.getId()
        );

        wallet.setBalance(wallet.getBalance().add(event.getAmount()));
        walletRepository.save(wallet);

        event.setLedgerReference(ledgerReference);
        event.setCreditedAt(LocalDateTime.now());
        event.setStatus(CustomerFundingEventStatus.CREDITED);
        eventRepository.save(event);

        return result(event, true, false);
    }

    private FundingCreditPolicy findExactPolicy(String providerCode) {
        FundingCreditPolicy match = null;
        for (FundingCreditPolicy candidate : policies) {
            if (candidate == null || candidate.providerCode() == null) {
                continue;
            }
            if (providerCode.equalsIgnoreCase(candidate.providerCode().trim())) {
                if (match != null) {
                    throw new IllegalStateException("Multiple funding credit policies are configured for provider");
                }
                match = candidate;
            }
        }
        return match;
    }

    private CreditResult reconciliation(CustomerFundingEvent event, String policyCode, String reason) {
        event.setCreditPolicyCode(normalizePolicyCode(policyCode));
        event.setCreditDecision(FundingCreditPolicy.Outcome.RECONCILIATION_REQUIRED.name());
        event.setCreditDecisionReason(normalizeReason(reason));
        event.setCreditDecidedAt(LocalDateTime.now());
        event.setStatus(CustomerFundingEventStatus.RECONCILIATION_REQUIRED);
        eventRepository.save(event);
        return result(event, false, false);
    }

    private CreditResult result(CustomerFundingEvent event, boolean credited, boolean idempotent) {
        return new CreditResult(
                event.getId(),
                event.getStatus(),
                credited,
                idempotent,
                event.getCreditPolicyCode(),
                event.getCreditDecision(),
                event.getLedgerReference()
        );
    }

    private void validateFinancialFields(CustomerFundingEvent event) {
        BigDecimal amount = event.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Funding amount is invalid");
        }
        if (event.getCurrency() == null || event.getCurrency().trim().length() != 3) {
            throw new IllegalArgumentException("Funding currency is invalid");
        }
    }

    private String normalizeProvider(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Funding provider is required");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 40 || !normalized.matches("[A-Z0-9_\\-]+")) {
            throw new IllegalArgumentException("Invalid funding provider");
        }
        return normalized;
    }

    private String normalizePolicyCode(String value) {
        if (value == null || value.isBlank()) {
            return "UNCONFIGURED";
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.length() > 80 || !normalized.matches("[A-Z0-9_\\-]+")) {
            throw new IllegalStateException("Invalid funding credit policy code");
        }
        return normalized;
    }

    private String normalizeReason(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 255 ? normalized : normalized.substring(0, 255);
    }

    public record CreditResult(
            UUID eventId,
            CustomerFundingEventStatus status,
            boolean credited,
            boolean idempotent,
            String policyCode,
            String decision,
            String ledgerReference
    ) {
    }
}
