package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.dto.WaecServiceDtos.RegistrationPurchaseRequest;
import com.wavetransakt.serviceprovider.dto.WaecServiceDtos.ResultCheckerPurchaseRequest;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaecPaymentServiceTest {

    @Test
    void staleClientPriceNeverReservesWalletOrCallsProviderPurchase() {
        VtpassCatalogClient catalog = mock(VtpassCatalogClient.class);
        VtpassPurchaseClient purchaseClient = mock(VtpassPurchaseClient.class);
        ServicePaymentReservationService reservationService = mock(ServicePaymentReservationService.class);
        ServicePaymentResponseMapper responseMapper = mock(ServicePaymentResponseMapper.class);

        when(catalog.getProviders("education")).thenReturn(List.of(
                provider("waec", "WAEC Result Checker PIN")
        ));
        when(catalog.getVariations("waec")).thenReturn(
                variations("waec", "WAEC Result Checker PIN", "waecdirect", "WASSCE", 900)
        );

        WaecPaymentService service = new WaecPaymentService(
                catalog,
                purchaseClient,
                reservationService,
                responseMapper
        );

        ResultCheckerPurchaseRequest request = new ResultCheckerPurchaseRequest(
                "waecdirect",
                BigDecimal.valueOf(800),
                "08012345678",
                "123456"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchaseResultChecker(
                        UUID.randomUUID(),
                        "idem-waec-0001",
                        request
                )
        );

        verify(purchaseClient).validateConfigured();
        verify(reservationService, never()).reserve(any(), anyString(), any());
        verify(purchaseClient, never()).purchase(
                anyString(), anyString(), any(), any(), anyString(), any(), any(), anyString()
        );
    }

    @Test
    void staleRegistrationPriceNeverReservesWalletOrCallsProviderPurchase() {
        VtpassCatalogClient catalog = mock(VtpassCatalogClient.class);
        VtpassPurchaseClient purchaseClient = mock(VtpassPurchaseClient.class);
        ServicePaymentReservationService reservationService = mock(ServicePaymentReservationService.class);
        ServicePaymentResponseMapper responseMapper = mock(ServicePaymentResponseMapper.class);

        when(catalog.getProviders("education")).thenReturn(List.of(
                provider("waec-registration", "WAEC Registration PIN")
        ));
        when(catalog.getVariations("waec-registration")).thenReturn(
                variations(
                        "waec-registration",
                        "WAEC Registration PIN",
                        "waec-registraion",
                        "WASSCE Private Candidates",
                        14450
                )
        );

        WaecPaymentService service = new WaecPaymentService(
                catalog,
                purchaseClient,
                reservationService,
                responseMapper
        );

        RegistrationPurchaseRequest request = new RegistrationPurchaseRequest(
                "waec-registraion",
                BigDecimal.valueOf(14000),
                "08012345678",
                "123456"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchaseRegistrationPin(
                        UUID.randomUUID(),
                        "idem-waec-reg-0001",
                        request
                )
        );

        verify(purchaseClient).validateConfigured();
        verify(reservationService, never()).reserve(any(), anyString(), any());
        verify(purchaseClient, never()).purchase(
                anyString(), anyString(), any(), any(), anyString(), any(), any(), anyString()
        );
    }

    private Provider provider(String serviceId, String name) {
        return new Provider(
                serviceId,
                name,
                BigDecimal.ONE,
                BigDecimal.valueOf(100000),
                "N0.00",
                "fix",
                null
        );
    }

    private VariationList variations(
            String serviceId,
            String serviceName,
            String code,
            String name,
            long amount
    ) {
        return new VariationList(
                serviceId,
                serviceName,
                List.of(new Variation(
                        code,
                        name,
                        BigDecimal.valueOf(amount),
                        true
                ))
        );
    }
}
