package com.wavetransakt.serviceprovider.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ServiceCatalogDtos {

    private ServiceCatalogDtos() {
    }

    public record Category(
            String identifier,
            String name
    ) {
    }

    public record Provider(
            String serviceId,
            String name,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            String convenienceFee,
            String productType,
            String imageUrl
    ) {
    }

    public record Variation(
            String code,
            String name,
            BigDecimal amount,
            boolean fixedPrice
    ) {
    }

    public record VariationList(
            String serviceId,
            String serviceName,
            List<Variation> variations
    ) {
    }
}
