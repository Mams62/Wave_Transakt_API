package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServicePurchaseRequest;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassVerificationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServicePaymentServiceTest {

    @Test
    void unverifiedElectricityCustomerNeverReservesWalletOrCallsPurchase() {
        VtpassCatalogClient catalog = mock(VtpassCatalogClient.class);
        VtpassPurchaseClient purchase = mock(VtpassPurchaseClient.class);
        VtpassVerificationClient verification = mock(VtpassVerificationClient.class);
        ServicePaymentReservationService reservation = mock(ServicePaymentReservationService.class);
        ServicePaymentResponseMapper responseMapper = mock(ServicePaymentResponseMapper.class);

        when(catalog.getProviders("electricity-bill")).thenReturn(List.of(
                new Provider(
                        "abuja-electric",
                        "Abuja Electricity",
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(100000),
                        null,
                        null,
                        null
                )
        ));
        when(verification.verify(
                "ELECTRICITY",
                "abuja-electric",
                "12345678901",
                "prepaid"
        )).thenReturn(new VerifyResponse(
                "VTPASS",
                "ELECTRICITY",
                "abuja-electric",
                "12345678901",
                "",
                "",
                "",
                "",
                null,
                null,
                false,
                "Meter number could not be verified"
        ));

        ServicePaymentService service = new ServicePaymentService(
                catalog,
                purchase,
                verification,
                reservation,
                responseMapper
        );

        ServicePurchaseRequest request = new ServicePurchaseRequest(
                "ELECTRICITY",
                "abuja-electric",
                null,
                BigDecimal.valueOf(1000),
                "12345678901",
                "08012345678",
                "prepaid",
                "123456"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchase(UUID.randomUUID(), "idem-electricity-001", request)
        );

        verify(purchase).validateConfigured();
        verify(verification).verify(
                "ELECTRICITY",
                "abuja-electric",
                "12345678901",
                "prepaid"
        );
        verify(reservation, never()).reserve(any(), anyString(), any());
        verify(purchase, never()).purchase(
                anyString(), anyString(), any(), any(), anyString(), any(), any(), anyString()
        );
    }
}
