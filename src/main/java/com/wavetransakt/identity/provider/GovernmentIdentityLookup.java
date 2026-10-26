package com.wavetransakt.identity.provider;

import java.time.LocalDate;

/**
 * One normalized, server-only identity contract for approved providers.
 * Raw identity lookups are never exposed directly through a public API.
 */
public interface GovernmentIdentityLookup {
    boolean isConfigured();
    IdentityRecord lookupBvn(String bvn);
    IdentityRecord lookupNin(String nin);

    record IdentityRecord(
            String firstName,
            String lastName,
            String middleName,
            String gender,
            LocalDate dateOfBirth,
            String providerReference
    ) {}
}
