package com.wavetransakt.serviceprovider.service;

import com.wavetransakt.serviceprovider.dto.ServiceCatalogDtos.Provider;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.Account;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.PurchaseRequest;
import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailResponse;
import com.wavetransakt.serviceprovider.vtpass.VtpassCatalogClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient;
import com.wavetransakt.serviceprovider.vtpass.VtpassSmileClient;
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

class SmilePaymentServiceTest {

    @Test
    void accountNotLinkedToVerifiedEmailNeverReservesWalletOrCallsPurchase() {
        VtpassCatalogClient catalog = mock(VtpassCatalogClient.class);
        VtpassSmileClient smileClient = mock(VtpassSmileClient.class);
        VtpassPurchaseClient purchaseClient = mock(VtpassPurchaseClient.class);
        ServicePaymentReservationService reservationService = mock(ServicePaymentReservationService.class);

        when(catalog.getProviders("data")).thenReturn(List.of(
                new Provider(
                        "smile-direct",
                        "Smile Payment",
                        BigDecimal.valueOf(100),
                        BigDecimal.valueOf(150000),
                        null,
                        "fix",
                        null
                )
        ));

        VerifyEmailResponse verification = new VerifyEmailResponse(
                "VTPASS",
                "smile-direct",
                "tester@sandbox.com",
                "THE TESTER ITSELF",
                List.of(new Account("08011111111", "TESTER1")),
                true,
                "Smile email verified"
        );

        when(smileClient.verifyEmail("tester@sandbox.com")).thenReturn(verification);
        when(smileClient.normalizeAccountId("08099999999")).thenReturn("08099999999");
        when(smileClient.containsAccount(verification, "08099999999")).thenReturn(false);

        SmilePaymentService service = new SmilePaymentService(
                catalog,
                smileClient,
                purchaseClient,
                reservationService
        );

        PurchaseRequest request = new PurchaseRequest(
                "tester@sandbox.com",
                "08099999999",
                "516",
                BigDecimal.valueOf(510),
                "08012345678",
                "123456"
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.purchase(UUID.randomUUID(), "idem-smile-0001", request)
        );

        verify(purchaseClient).validateConfigured();
        verify(smileClient).verifyEmail("tester@sandbox.com");
        verify(reservationService, never()).reserve(any(), anyString(), any());
        verify(purchaseClient, never()).purchase(
                anyString(), anyString(), any(), any(), anyString(), any(), any(), anyString()
        );
    }
}
