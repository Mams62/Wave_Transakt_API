package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.dto.ServicePurchaseRequest;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
import com.wavetransakt.serviceprovider.entity.ServicePayment;
import com.wavetransakt.serviceprovider.entity.ServicePaymentStatus;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.CanonicalServiceRequest;
import com.wavetransakt.serviceprovider.service.ServicePaymentReservationService.Reservation;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import com.wavetransakt.serviceprovider.vtpass.VtpassVerificationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ServicePaymentService {

    private final VtpassCatalogClient catalogClient;
    private final VtpassPurchaseClient purchaseClient;
    private final VtpassVerificationClient verificationClient;
    private final ServicePaymentReservationService reservationService;
    private final ServicePaymentResponseMapper responseMapper;

    public ServicePaymentResponse purchase(UUID userId, String idempotencyKey, ServicePurchaseRequest request) {
        if (request == null) throw new IllegalArgumentException("Service purchase request is required");

        purchaseClient.validateConfigured();
        CanonicalServiceRequest canonical = resolveCanonicalRequest(request);
        Reservation reservation = reservationService.reserve(userId, idempotencyKey, canonical);
        ServicePayment payment = reservation.payment();

        if (!reservation.created()) return responseMapper.toResponse(payment);

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
                reservationService.applyProviderResult(userId, payment.getId(), providerResult)
        );
    }

    public ServicePaymentResponse getPayment(UUID userId, String reference) {
        return responseMapper.toResponse(
                reservationService.findOwned(userId, normalizeReference(reference))
        );
    }

    public ServicePaymentResponse requery(UUID userId, String reference) {
        ServicePayment payment = reservationService.findOwned(userId, normalizeReference(reference));
        if (payment.getStatus() != ServicePaymentStatus.PENDING) {
            return responseMapper.toResponse(payment);
        }

        purchaseClient.validateConfigured();
        ProviderResult result = purchaseClient.requery(payment.getProviderRequestId());
        return responseMapper.toResponse(
                reservationService.applyProviderResult(userId, payment.getId(), result)
        );
    }

    private CanonicalServiceRequest resolveCanonicalRequest(ServicePurchaseRequest request) {
        String kind = request.serviceKind().trim().toUpperCase(Locale.ROOT);
        String catalogIdentifier = switch (kind) {
            case "AIRTIME" -> "airtime";
            case "DATA" -> "data";
            case "ELECTRICITY" -> "electricity-bill";
            case "TV" -> "tv-subscription";
            case "INTERNET" -> "data";
            default -> throw new IllegalArgumentException("Unsupported service kind");
        };

        String serviceId = request.serviceId().trim();
        Provider provider = catalogClient.getProviders(catalogIdentifier).stream()
                .filter(item -> serviceId.equalsIgnoreCase(item.serviceId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Selected provider is not available in the connected provider catalog"));

        String recipient = request.recipient().trim();
        String customerPhone = null;
        String serviceOption = null;
        String variationCode = null;
        BigDecimal amount;

        switch (kind) {
            case "AIRTIME" -> {
                recipient = normalizePhone(recipient, "Recipient");
                customerPhone = recipient;
                if (hasText(request.variationCode())) throw new IllegalArgumentException("Airtime does not use a variation code");
                amount = normalizeAmount(request.amount());
            }
            case "DATA" -> {
                recipient = normalizePhone(recipient, "Recipient");
                customerPhone = recipient;
                variationCode = requireText(request.variationCode(), "A data bundle variation is required");
                Variation variation = requireVariation(provider.serviceId(), variationCode, "Selected data bundle is no longer available");
                amount = resolveVariationAmount(request.amount(), variation, "The selected data bundle price has changed; refresh plans and try again");
            }
            case "ELECTRICITY" -> {
                serviceOption = normalizeMeterType(request.option());
                variationCode = serviceOption;
                customerPhone = normalizePhone(request.customerPhone(), "Customer phone");
                VerifyResponse verification = verificationClient.verify(kind, provider.serviceId(), recipient, serviceOption);
                requireVerified(verification);
                amount = normalizeAmount(request.amount());
                if (verification.minimumAmount() != null && amount.compareTo(verification.minimumAmount()) < 0) {
                    throw new IllegalArgumentException("Amount is below the verified customer minimum of NGN " + verification.minimumAmount());
                }
            }
            case "TV" -> {
                serviceOption = normalizeTvOption(request.option());
                customerPhone = normalizePhone(request.customerPhone(), "Customer phone");
                variationCode = requireText(request.variationCode(), "A TV bouquet variation is required");
                VerifyResponse verification = verificationClient.verify(kind, provider.serviceId(), recipient, null);
                requireVerified(verification);
                Variation variation = requireVariation(provider.serviceId(), variationCode, "Selected TV bouquet is no longer available");
                amount = resolveVariationAmount(request.amount(), variation, "The selected TV bouquet price has changed; refresh bouquets and try again");
            }
            case "INTERNET" -> {
                if (!"spectranet".equalsIgnoreCase(provider.serviceId())) {
                    throw new IllegalArgumentException("This internet provider requires a separate verified account flow and is not enabled yet");
                }
                recipient = normalizePhone(recipient, "Spectranet account phone");
                customerPhone = normalizePhone(request.customerPhone(), "Customer phone");
                variationCode = requireText(request.variationCode(), "A Spectranet plan variation is required");
                Variation variation = requireVariation(provider.serviceId(), variationCode, "Selected Spectranet plan is no longer available");
                amount = resolveVariationAmount(request.amount(), variation, "The selected Spectranet plan price has changed; refresh plans and try again");
                serviceOption = "quantity:1";
            }
            default -> throw new IllegalArgumentException("Unsupported service kind");
        }

        validateProviderLimits(provider, amount);
        return new CanonicalServiceRequest(kind, provider.serviceId(), provider.name(), variationCode, recipient, customerPhone, serviceOption, amount, request.transactionPin());
    }

    private Variation requireVariation(String serviceId, String variationCode, String message) {
        VariationList variationList = catalogClient.getVariations(serviceId);
        return variationList.variations().stream()
                .filter(item -> variationCode.equalsIgnoreCase(item.code()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(message));
    }

    private BigDecimal resolveVariationAmount(BigDecimal requestedAmount, Variation variation, String changedPriceMessage) {
        if (variation.amount() == null) return normalizeAmount(requestedAmount);
        BigDecimal currentAmount = normalizeAmount(variation.amount());
        if (requestedAmount != null && variation.fixedPrice() && normalizeAmount(requestedAmount).compareTo(currentAmount) != 0) {
            throw new IllegalArgumentException(changedPriceMessage);
        }
        return currentAmount;
    }

    private void requireVerified(VerifyResponse verification) {
        if (verification == null || !verification.valid()) {
            String message = verification == null ? null : verification.message();
            throw new IllegalArgumentException(message == null || message.isBlank() ? "Customer reference could not be verified" : message);
        }
    }

    private String normalizePhone(String value, String label) {
        String phone = value == null ? "" : value.trim();
        if (!phone.matches("\\d{10,15}")) throw new IllegalArgumentException(label + " must contain 10 to 15 digits");
        return phone;
    }

    private String normalizeMeterType(String value) {
        String option = requireText(value, "Electricity meter type is required").toLowerCase(Locale.ROOT);
        if (!option.equals("prepaid") && !option.equals("postpaid")) throw new IllegalArgumentException("Electricity meter type must be prepaid or postpaid");
        return option;
    }

    private String normalizeTvOption(String value) {
        String option = value == null || value.isBlank() ? "change" : value.trim().toLowerCase(Locale.ROOT);
        if (!option.equals("change")) throw new IllegalArgumentException("Only TV bouquet change/top-up is enabled in this stage; renewal requires the provider renewal amount flow");
        return option;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private void validateProviderLimits(Provider provider, BigDecimal amount) {
        if (provider.minimumAmount() != null && amount.compareTo(provider.minimumAmount()) < 0) {
            throw new IllegalArgumentException("Amount is below the provider minimum of NGN " + provider.minimumAmount());
        }
        if (provider.maximumAmount() != null && amount.compareTo(provider.maximumAmount()) > 0) {
            throw new IllegalArgumentException("Amount is above the provider maximum of NGN " + provider.maximumAmount());
        }
    }

    private BigDecimal normalizeAmount(BigDecimal amount) {
        if (amount == null) throw new IllegalArgumentException("Amount is required");
        BigDecimal normalized;
        try { normalized = amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Amount must not contain more than 2 decimal places"); }
        if (normalized.compareTo(BigDecimal.ONE) < 0) throw new IllegalArgumentException("Amount must be at least NGN 1.00");
        return normalized;
    }

    private String normalizeReference(String reference) {
        if (reference == null || reference.isBlank() || reference.trim().length() > 60) throw new IllegalArgumentException("Invalid service payment reference");
        return reference.trim();
    }
}
