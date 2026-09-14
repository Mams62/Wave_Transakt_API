package com.wavetransakt.serviceprovider.vtpass;

import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderOutcome;
import com.wavetransakt.serviceprovider.vtpass.VtpassPurchaseClient.ProviderResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VtpassJambPurchaseClientTest {

    @Test
    void sendsVerifiedProfileIdAndParsesPurchasedCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassPurchaseClient client = new VtpassPurchaseClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "https://sandbox.vtpass.com/api/");
        ReflectionTestUtils.setField(client, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(client, "secretKey", "test-secret-key");

        server.expect(requestTo("https://sandbox.vtpass.com/api/pay"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("serviceID=jamb"),
                        org.hamcrest.Matchers.containsString("variation_code=utme-mock"),
                        org.hamcrest.Matchers.containsString("billersCode=0123456789"),
                        org.hamcrest.Matchers.containsString("phone=08012345678")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{"transactions":{"status":"delivered","transactionId":"JAMB-TXN-1"}},
                          "response_description":"TRANSACTION SUCCESSFUL",
                          "purchased_code":"Pin : TEST-JAMB-EPIN"
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderResult result = client.purchase(
                "EDUCATION",
                "jamb",
                "utme-mock",
                BigDecimal.valueOf(7700),
                "0123456789",
                "08012345678",
                "verified-jamb-profile",
                "202609141545jambtest0001"
        );

        assertEquals(ProviderOutcome.SUCCESS, result.outcome());
        assertEquals("JAMB-TXN-1", result.transactionId());
        assertEquals("Pin : TEST-JAMB-EPIN", result.fulfillment());
        server.verify();
    }
}
