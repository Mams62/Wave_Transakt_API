package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
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
                new Provider(
                        "waec",
                        "WAEC Result Checker PIN",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(100000),
                        "N0.00",
                        "fix",
                        null
                )
        ));
        when(catalog.getVariations("waec")).thenReturn(
                new VariationList(
                        "waec",
                        "WAEC Result Checker PIN",
                        List.of(new Variation(
                                "waecdirect",
                                "WASSCE",
                                BigDecimal.valueOf(900),
                                true
                        ))
                )
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
}
