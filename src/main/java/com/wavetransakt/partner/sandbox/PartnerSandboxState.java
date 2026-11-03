package com.wavetransakt.partner.sandbox;

/** States are SIMULATION-ONLY. None proves a real Wema settlement. */
public enum PartnerSandboxState {
    CREATED, PENDING, TEST_CONFIRMED, TEST_FAILED, REVIEW_REQUIRED;

    public boolean terminal() {
        return this == TEST_CONFIRMED || this == TEST_FAILED || this == REVIEW_REQUIRED;
    }

    /** Fail closed: any contradictory provider event requires manual review. */
    public PartnerSandboxState next(String authenticatedSimulationOutcome) {
        PartnerSandboxState target = switch (authenticatedSimulationOutcome) {
            case "PENDING" -> PENDING;
            case "CONFIRMED" -> TEST_CONFIRMED;
            case "FAILED" -> TEST_FAILED;
            default -> throw new IllegalArgumentException("Unknown sandbox outcome");
        };
        if (this == target) return this;
        if (terminal()) return REVIEW_REQUIRED;
        if (this == CREATED || this == PENDING) return target;
        return REVIEW_REQUIRED;
    }
}
