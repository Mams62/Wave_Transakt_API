package com.wavetransakt.wallet.funding;

/**
 * Exact-provider policy boundary for deciding whether a VERIFIED provider
 * funding event is sufficiently final to become spendable Wave balance.
 *
 * No default ALLOW policy exists. A provider must have an explicit policy
 * implementation added from approved provider documentation/contract terms.
 */
public interface FundingCreditPolicy {

    String providerCode();

    String policyCode();

    CreditDecision evaluate(CustomerFundingEvent event);

    enum Outcome {
        ALLOW,
        RECONCILIATION_REQUIRED,
        DENY
    }

    record CreditDecision(
            Outcome outcome,
            String reason
    ) {
        public CreditDecision {
            if (outcome == null) {
                throw new IllegalArgumentException("Funding credit outcome is required");
            }
        }

        public static CreditDecision allow(String reason) {
            return new CreditDecision(Outcome.ALLOW, reason);
        }

        public static CreditDecision reconciliation(String reason) {
            return new CreditDecision(Outcome.RECONCILIATION_REQUIRED, reason);
        }

        public static CreditDecision deny(String reason) {
            return new CreditDecision(Outcome.DENY, reason);
        }
    }
}
