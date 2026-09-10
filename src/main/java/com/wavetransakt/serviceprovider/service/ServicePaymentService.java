package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.dto.ServicePurchaseRequest;
import com.wavetransakt.serviceprovider.entity.ServicePayment;
import com.wavetransakt.serviceprovider.entity.ServicePaymentStatus;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.CanonicalServiceRequest;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.Reservation;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServicePaymentService {

    private final VtpassCatalogClient catalogClient;
    private final VtpassPurchaseClient purchaseClient;
    private final ServicePaymentReservationService reservationService;

    public ServicePaymentResponse purchase(
            UUID userId,
            String idempotencyKey,
            ServicePurchaseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Service purchase request is required");
        }

        purchaseClient.validateConfigured();
        CanonicalServiceRequest canonical = resolveCanonicalRequest(request);

        Reservation reservation = reservationService.reserve(
                userId,
                idempotencyKey,
                canonical
        );

        ServicePayment payment = reservation.payment();

        // An idempotent replay never calls the provider again.
        if (!reservation.created()) {
            return toResponse(payment);
        }

        ProviderResult providerResult = purchaseClient.purchase(
                payment.getServiceKind(),
                payment.getServiceId(),
                payment.getVariationCode(),
                payment.getAmount(),
                payment.getRecipient(),
                payment.getProviderRequestId()
        );

        ServicePayment resolved = reservationService.applyProviderResult(
                userId,
                payment.getId(),
                providerResult
        );

        return toResponse(resolved);
    }

    public ServicePaymentResponse getPayment(UUID userId, String reference) {
        return toResponse(
                reservationService.findOwned(userId, normalizeReference(reference))
        );
    }

    public ServicePaymentResponse requery(UUID userId, String reference) {
        ServicePayment payment = reservationService.findOwned(
                userId,
                normalizeReference(reference)
        );

        if (payment.getStatus() != ServicePaymentStatus.PENDING) {
            return toResponse(payment);
        }

        purchaseClient.validateConfigured();
        ProviderResult result = purchaseClient.requery(payment.getProviderRequestId());

        return toResponse(
                reservationService.applyProviderResult(userId, payment.getId(), result)
        );
    }

    private CanonicalServiceRequest resolveCanonicalRequest(ServicePurchaseRequest request) {
        String kind = request.serviceKind().trim().toUpperCase(Locale.ROOT);
        String catalogIdentifier = switch (kind) {
            case "AIRTIME" -> "airtime";
            case "DATA" -> "data";
            default -> throw new IllegalArgumentException(
                    "Only AIRTIME and DATA are enabled in the controlled service-payment stage"
            );
        };

        String serviceId = request.serviceId().trim();
        Provider provider = catalogClient.getProviders(catalogIdentifier)
                .stream()
                .filter(item -> serviceId.equalsIgnoreCase(item.serviceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected provider is not available in the connected sandbox catalog"
                ));

        String recipient = request.recipient().trim();
        BigDecimal amount;
        String variationCode = null;

        if ("DATA".equals(kind)) {
            if (request.variationCode() == null || request.variationCode().isBlank()) {
                throw new IllegalArgumentException("A data bundle variation is required");
            }

            variationCode = request.variationCode().trim();
            VariationList variationList = catalogClient.getVariations(provider.serviceId());
            String requestedVariation = variationCode;
            Variation variation = variationList.variations()
                    .stream()
                    .filter(item -> requestedVariation.equalsIgnoreCase(item.code()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Selected data bundle is no longer available"
                    ));

            if (variation.amount() != null) {
                amount = normalizeAmount(variation.amount());
                if (request.amount() != null && variation.fixedPrice() &&
                        normalizeAmount(request.amount()).compareTo(amount) != 0) {
                    throw new IllegalArgumentException(
                            "The selected data bundle price has changed; refresh plans and try again"
                    );
                }
            } else {
                amount = normalizeAmount(request.amount());
            }
        } else {
            if (request.variationCode() != null && !request.variationCode().isBlank()) {
                throw new IllegalArgumentException("Airtime does not use a variation code");
            }
            amount = normalizeAmount(request.amount());
        }

        validateProviderLimits(provider, amount);

        return new CanonicalServiceRequest(
                kind,
                provider.serviceId(),
                provider.name(),
                variationCode,
                recipient,
                amount,
                request.transactionPin()
        );
    }

    private void validateProviderLimits(Provider provider, BigDecimal amount) {
        if (provider.minimumAmount() != null && amount.compareTo(provider.minimumAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Amount is below the provider minimum of NGN " + provider.minimumAmount()
            );
        }
        if (provider.maximumAmount() != null && amount.compareTo(provider.maximumAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Amount is above the provider maximum of NGN " + provider.maximumAmount()
            );
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        BigDecimal normalized;
        try {
            normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount must not contain more than 2 decimal places");
        }
        if (normalized.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("Amount must be at least NGN 1.00");
        }
        return normalized;
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank() || reference.trim().length() > 60) {
            throw new IllegalArgumentException("Invalid service payment reference");
        }
        return reference.trim();
    }

    private ServicePaymentResponse toResponse(ServicePayment payment) {
        return new ServicePaymentResponse(
                payment.getId(),
                payment.getReference(),
                payment.getProviderRequestId(),
                payment.getServiceKind(),
                payment.getServiceId(),
                payment.getServiceName(),
                payment.getVariationCode(),
                payment.getRecipient(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getProviderStatus(),
                payment.getProviderTransactionId(),
                payment.getProviderMessage(),
                payment.getCreatedAt()
        );
    }
}
