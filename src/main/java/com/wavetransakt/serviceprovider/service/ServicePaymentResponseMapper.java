package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServicePaymentResponse;
import com.wavetransakt.serviceprovider.entity.ServicePayment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ServicePaymentResponseMapper {

    private final ServiceFulfillmentCrypto fulfillmentCrypto;

    public ServicePaymentResponse toResponse(ServicePayment payment) {
        String fulfillment = null;
        if (payment.getProviderFulfillmentCiphertext() != null &&
                !payment.getProviderFulfillmentCiphertext().isBlank()) {
            fulfillment = fulfillmentCrypto.decrypt(
                    payment.getProviderFulfillmentCiphertext()
            );
        }

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
                fulfillment,
                payment.getCreatedAt()
        );
    }
}
