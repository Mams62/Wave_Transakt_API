package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.JambServiceDtos.PurchaseRequest;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Variation;
import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.VariationList;
import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassVerificationClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JambPaymentServiceTest {

    @Test
    void rejectedProfileStopsBeforeReservation() {
        VtpassCatalogClient catalog = mock(VtpassCatalogClient.class);
        VtpassVerificationClient verifier = mock(VtpassVerificationClient.class);
        VtpassPurchaseClient purchase = mock(VtpassPurchaseClient.class);
        ServicePaymentReservationService reservation = mock(ServicePaymentReservationService.class);
        ServicePaymentResponseMapper mapper = mock(ServicePaymentResponseMapper.class);

        when(catalog.getProviders("education")).thenReturn(List.of(
                new Provider("jamb", "JAMB", BigDecimal.ONE, BigDecimal.valueOf(100000), null, "fix", null)
        ));
        when(catalog.getVariations("jamb")).thenReturn(
                new VariationList("jamb", "JAMB", List.of(
                        new Variation("utme-mock", "UTME PIN", BigDecimal.valueOf(7700), true)
                ))
        );
        when(verifier.verify("EDUCATION", "jamb", "0123456789", "utme-mock"))
                .thenReturn(new VerifyResponse(
                        "VTPASS", "EDUCATION", "jamb", "0123456789",
                        "", "", "", "", null, null, false, "Profile not verified"
                ));

        JambPaymentService service = new JambPaymentService(
                catalog, verifier, purchase, reservation, mapper
        );

        PurchaseRequest request = new PurchaseRequest(
                "utme-mock", BigDecimal.valueOf(7700), "0123456789", "08012345678", "000000"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchase(UUID.randomUUID(), "idem-jamb-test", request)
        );

        verify(verifier).verify("EDUCATION", "jamb", "0123456789", "utme-mock");
        verify(reservation, never()).reserve(any(), anyString(), any());
        verify(purchase, never()).purchase(anyString(), anyString(), any(), any(), anyString(), any(), any(), anyString());
    }
}
