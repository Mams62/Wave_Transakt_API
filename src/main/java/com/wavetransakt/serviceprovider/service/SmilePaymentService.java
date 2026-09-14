package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.PurchaseRequest;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailResponse;
import com.wavetransakt.serviceprovider.entity.ServicePayment;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.CanonicalServiceRequest;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.Reservation;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import com.wavetransakt.serviceprovider.vtpass.VtpassSmileClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SmilePaymentService {

    private static final String SERVICE_ID = "smile-direct";

    private final VtpassCatalogClient catalogClient;
    private final VtpassSmileClient smileClient;
    private final VtpassPurchaseClient purchaseClient;
    private final ServicePaymentReservationService reservationService;
    private final ServicePaymentResponseMapper responseMapper;

    public ServicePaymentResponse purchase(
            UUID userId,
            String idempotencyKey,
            PurchaseRequest request
    ) {
        if (request == null) {
            throw new IllegalArgumentException("Smile purchase request is required");
        }

        purchaseClient.validateConfigured();

        Provider provider = catalogClient.getProviders("data")
                .stream()
                .filter(item -> SERVICE_ID.equalsIgnoreCase(item.serviceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Smile is not available in the connected provider catalog"
                ));

        VerifyEmailResponse verification = smileClient.verifyEmail(request.email());
        if (!verification.valid()) {
            throw new IllegalArgumentException(
                    verification.message() == null || verification.message().isBlank()
                            ? "Smile email could not be verified"
                            : verification.message()
            );
        }

        String accountId = smileClient.normalizeAccountId(request.accountId());
        if (!smileClient.containsAccount(verification, accountId)) {
            throw new IllegalArgumentException(
                    "Selected Smile account is not linked to the verified email"
            );
        }

        String variationCode = request.variationCode().trim();
        Variation variation = catalogClient.getVariations(SERVICE_ID)
                .variations()
                .stream()
                .filter(item -> variationCode.equalsIgnoreCase(item.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Selected Smile plan is no longer available"
                ));

        BigDecimal amount = resolveVariationAmount(request.amount(), variation);
        validateProviderLimits(provider, amount);
        String customerPhone = normalizePhone(request.customerPhone());

        CanonicalServiceRequest canonical = new CanonicalServiceRequest(
                "INTERNET",
                SERVICE_ID,
                provider.name(),
                variationCode,
                accountId,
                customerPhone,
                "verified-smile-account",
                amount,
                request.transactionPin()
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
                    "The selected Smile plan price has changed; refresh plans and try again"
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
