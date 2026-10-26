package com.wavetransakt.serviceprovider.vtpass;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VtpassCatalogCategoryPolicyTest {

    @Test
    void supportsOnlyReviewedWaveUtilityAndEducationCategories() {
        assertTrue(VtpassCatalogClient.isSupportedCategory("airtime"));
        assertTrue(VtpassCatalogClient.isSupportedCategory("data"));
        assertTrue(VtpassCatalogClient.isSupportedCategory("electricity-bill"));
        assertTrue(VtpassCatalogClient.isSupportedCategory("tv-subscription"));
        assertTrue(VtpassCatalogClient.isSupportedCategory("education"));
        assertFalse(VtpassCatalogClient.isSupportedCategory("other-services"));
        assertFalse(VtpassCatalogClient.isSupportedCategory("insurance"));
        assertFalse(VtpassCatalogClient.isSupportedCategory(null));
        assertFalse(VtpassCatalogClient.isSupportedCategory(""));
    }
}
