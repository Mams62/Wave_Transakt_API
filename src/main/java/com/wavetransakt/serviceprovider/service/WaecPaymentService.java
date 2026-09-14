package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.dto.WaecServiceDtos.RegistrationPurchaseRequest;
import com.wavetransakt.serviceprovider.dto.WaecServiceDtos.ResultCheckerPurchaseRequest;
import com.wavetransakt.serviceprovider.entity.ServicePayment;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.CanonicalServiceRequest;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.Reservation;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WaecPaymentService {

    private static final String RESULT_CHECKER_SERVICE_ID = "waec";
    private static final String REGISTRATION_SERVICE_ID = "waec-registration";

    private final VtpassCatalogClient catalogClient;
    private final VtpassPurchaseClient purchaseClient;
    private final ServicePaymentReservationService reservationService;
    private final ServicePaymentResponseMapper responseMapper;

    public ServicePaymentResponse purchaseResultChecker(
            UUID userId,
            String idempotencyKey,
            ResultCheckerPurchaseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("WAEC result checker purchase request is required");
        }
        return purchaseProduct(
                userId,
                idempotencyKey,
                RESULT_CHECKER_SERVICE_ID,
                "WAEC Result Checker",
                request.variationCode(),
                request.amount(),
                request.customerPhone(),
                request.transactionPin()
        );
    }

    public ServicePaymentResponse purchaseRegistrationPin(
            UUID userId,
            String idempotencyKey,
            RegistrationPurchaseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("WAEC registration purchase request is required");
        }
        return purchaseProduct(
                userId,
                idempotencyKey,
                REGISTRATION_SERVICE_ID,
                "WAEC Registration PIN",
                request.variationCode(),
                request.amount(),
                request.customerPhone(),
                request.transactionPin()
        );
    }

    private ServicePaymentResponse purchaseProduct(
            UUID userId,
            String idempotencyKey,
            String serviceId,
            String productLabel,
            String rawVariationCode,
            BigDecimal requestedAmount,
            String rawCustomerPhone,
            String transactionPin
    ) {
        purchaseClient.validateConfigured();

        Provider provider = catalogClient.getProviders("education")
                .stream()
                .filter(item -> serviceId.equalsIgnoreCase(item.serviceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        productLabel + " is not available in the connected provider catalog"
                ));

        String variationCode = rawVariationCode == null ? "" : rawVariationCode.trim();
        if (variationCode.isBlank()) {
            throw new IllegalArgumentException(productLabel + " variation is required");
        }

        Variation variation = catalogClient.getVariations(serviceId)
                .variations()
                .stream()
                .filter(item -> variationCode.equalsIgnoreCase(item.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected " + productLabel + " option is no longer available"
                ));

        BigDecimal amount = resolveVariationAmount(requestedAmount, variation);
        validateProviderLimits(provider, amount);
        String phone = normalizePhone(rawCustomerPhone);

        CanonicalServiceRequest canonical = new CanonicalServiceRequest(
                "EDUCATION",
                serviceId,
                provider.name(),
                variationCode,
                phone,
                phone,
                "quantity:1",
                amount,
                transactionPin
        );

        Reservation reservation = reservationService.reserve(
                userId,
                idempotencyKey,
                canonical
        );

        ServicePayment payment = reservation.payment();
        if (!reservation.created()) {
            return responseMapper.toResponse(payment);
        }

        ProviderResult providerResult = purchaseClient.purchase(
                payment.getServiceKind(),
                payment.getServiceId(),
                payment.getVariationCode(),
                payment.getAmount(),
                payment.getRecipient(),
                payment.getCustomerPhone(),
                payment.getServiceOption(),
                payment.getProviderRequestId()
        );

        return responseMapper.toResponse(
                reservationService.applyProviderResult(
                        userId,
                        payment.getId(),
                        providerResult
                )
        );
    }

    private BigDecimal resolveVariationAmount(
            BigDecimal requestedAmount,
            Variation variation
    ) {
        if (variation.amount() == null) {
            return normalizeAmount(requestedAmount);
        }

        BigDecimal currentAmount = normalizeAmount(variation.amount());
        if (requestedAmount != null && variation.fixedPrice() &&
                normalizeAmount(requestedAmount).compareTo(currentAmount) != 0) {
            throw new IllegalArgumentException(
                    "The selected WAEC price has changed; refresh options and try again"
            );
        }
        return currentAmount;
    }

    private void validateProviderLimits(Provider provider, BigDecimal amount) {
        if (provider.minimumAmount() != null &&
                amount.compareTo(provider.minimumAmount()) < 0) {
            throw new IllegalArgumentException(
                    "Amount is below the provider minimum of NGN " + provider.minimumAmount()
            );
        }
        if (provider.maximumAmount() != null &&
                amount.compareTo(provider.maximumAmount()) > 0) {
            throw new IllegalArgumentException(
                    "Amount is above the provider maximum of NGN " + provider.maximumAmount()
            );
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        try {
            BigDecimal normalized = amount.setScale(2, RoundingMode.UNNECESSARY);
            if (normalized.compareTo(BigDecimal.ONE) < 0) {
                throw new IllegalArgumentException("Amount must be at least NGN 1.00");
            }
            return normalized;
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException(
                    "Amount must not contain more than 2 decimal places"
            );
        }
    }

    private String normalizePhone(String rawPhone) {
        String phone = rawPhone == null ? "" : rawPhone.trim();
        if (!phone.matches("\\d{10,15}")) {
            throw new IllegalArgumentException(
                    "Customer phone must contain 10 to 15 digits"
            );
        }
        return phone;
    }
}
