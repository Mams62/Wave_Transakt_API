package com.wavetransakt.wallet.transfer;

import com.wavetransakt.wallet.interswitch.InterswitchAuthClient;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class InterswitchBankTransferGatewayTest {

    @Test
    void disabledTransferProductFailsClosed() {
        RestTemplate restTemplate = new RestTemplate();
        InterswitchAuthClient auth = mock(InterswitchAuthClient.class);
        when(auth.isConfigured()).thenReturn(false);

        InterswitchBankTransferGateway gateway = new InterswitchBankTransferGateway(restTemplate, auth);
        configure(gateway, false, false);

        BankTransferGateway.Readiness readiness = gateway.readiness();
        assertFalse(readiness.enabled());
        assertFalse(readiness.transferEnabled());
        assertEquals("DISABLED", readiness.status());
        assertThrows(RuntimeException.class, gateway::banks);
    }

    @Test
    void nameEnquiryUsesQuicktellerV5Contract() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        InterswitchAuthClient auth = mock(InterswitchAuthClient.class);
        when(auth.isConfigured()).thenReturn(true);
        when(auth.getAccessToken()).thenReturn("safe-access-token");

        InterswitchBankTransferGateway gateway = new InterswitchBankTransferGateway(restTemplate, auth);
        configure(gateway, true, false);

        server.expect(requestTo("https://qa.interswitchng.com/quicktellerservice/api/v5/transactions/DoAccountNameInquiry"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer safe-access-token"))
                .andExpect(header("TerminalId", "WAVETEST01"))
                .andExpect(header("bankCode", "044"))
                .andExpect(header("accountId", "0123456789"))
                .andRespond(withSuccess("""
                        {
                          "AccountName":"WAVE TEST BENEFICIARY",
                          "ResponseCode":"90000",
                          "ResponseCodeGrouping":"SUCCESSFUL"
                        }
                        """, MediaType.APPLICATION_JSON));

        BankTransferGateway.NameEnquiry result = gateway.resolveAccount("044", "0123456789");
        assertTrue(result.valid());
        assertEquals("WAVE TEST BENEFICIARY", result.accountName());
        assertEquals("90000", result.providerCode());
        server.verify();
    }

    @Test
    void transferUsesMinorUnitsAndMapsBeneficiaryBankUnavailableAsRetryable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        InterswitchAuthClient auth = mock(InterswitchAuthClient.class);
        when(auth.isConfigured()).thenReturn(true);
        when(auth.getAccessToken()).thenReturn("safe-access-token");

        InterswitchBankTransferGateway gateway = new InterswitchBankTransferGateway(restTemplate, auth);
        configure(gateway, true, true);

        server.expect(requestTo("https://qa.interswitchng.com/quicktellerservice/api/v5/transactions/TransferFunds"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer safe-access-token"))
                .andExpect(header("TerminalId", "WAVETEST01"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("\"transferCode\":\"1234"),
                        org.hamcrest.Matchers.containsString("\"amount\":\"250050\""),
                        org.hamcrest.Matchers.containsString("\"accountNumber\":\"0123456789\""),
                        org.hamcrest.Matchers.containsString("\"entityCode\":\"044\""),
                        org.hamcrest.Matchers.containsString("\"currencyCode\":\"566\""),
                        org.hamcrest.Matchers.containsString("\"initiatingEntityCode\":\"WAVE\""),
                        org.hamcrest.Matchers.containsString("\"mac\":")
                )))
                .andRespond(withSuccess("""
                        {
                          "ResponseCode":"70120",
                          "ResponseCodeGrouping":"FAILED",
                          "ResponseMessage":"Beneficiary bank unavailable"
                        }
                        """, MediaType.APPLICATION_JSON));

        String transferCode = gateway.deterministicTransferCode("WTBANK-20260914-0001");
        BankTransferGateway.TransferResult result = gateway.transfer(
                new BankTransferGateway.TransferCommand(
                        "WTBANK-20260914-0001",
                        transferCode,
                        new BigDecimal("2500.50"),
                        "044",
                        "0123456789",
                        "Beneficiary",
                        "Wave Test",
                        "Sender",
                        "Wave User",
                        "sender@example.com",
                        "08012345678"
                )
        );

        assertEquals(BankTransferGateway.Outcome.RETRYABLE, result.outcome());
        assertEquals("70120", result.providerCode());
        server.verify();
    }

    private void configure(
            InterswitchBankTransferGateway gateway,
            boolean enabled,
            boolean approved
    ) {
        ReflectionTestUtils.setField(gateway, "enabled", enabled);
        ReflectionTestUtils.setField(gateway, "productApproved", approved);
        ReflectionTestUtils.setField(gateway, "baseUrl", "https://qa.interswitchng.com/quicktellerservice/api/v5");
        ReflectionTestUtils.setField(gateway, "terminalId", "WAVETEST01");
        ReflectionTestUtils.setField(gateway, "initiatingEntityCode", "WAVE");
        ReflectionTestUtils.setField(gateway, "transferCodePrefix", "1234");
        ReflectionTestUtils.setField(gateway, "initiationPaymentMethodCode", "CA");
        ReflectionTestUtils.setField(gateway, "initiationChannel", "7");
        ReflectionTestUtils.setField(gateway, "terminationPaymentMethodCode", "AC");
        ReflectionTestUtils.setField(gateway, "terminationAccountType", "00");
    }
}
