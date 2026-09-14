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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VtpassWaecPurchaseClientTest {

    @Test
    void sendsWaecQuantityOneAndParsesPurchasedCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassPurchaseClient client = new VtpassPurchaseClient(restTemplate);

        configure(client);

        server.expect(requestTo("https://sandbox.vtpass.com/api/pay"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "api-key-value"))
                .andExpect(header("secret-key", "secret-key-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("serviceID=waec"),
                        org.hamcrest.Matchers.containsString("variation_code=waecdirect"),
                        org.hamcrest.Matchers.containsString("quantity=1"),
                        org.hamcrest.Matchers.containsString("phone=08012345678")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{
                            "transactions":{
                              "status":"delivered",
                              "transactionId":"1582290782154"
                            }
                          },
                          "response_description":"TRANSACTION SUCCESSFUL",
                          "purchased_code":"Serial No:WRN123456790, pin: 098765432112"
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderResult result = client.purchase(
                "EDUCATION",
                "waec",
                "waecdirect",
                BigDecimal.valueOf(900),
                "08012345678",
                "08012345678",
                "quantity:1",
                "202609141500waectest0001"
        );

        assertEquals(ProviderOutcome.SUCCESS, result.outcome());
        assertEquals("1582290782154", result.transactionId());
        assertEquals(
                "Serial No:WRN123456790, pin: 098765432112",
                result.fulfillment()
        );
        server.verify();
    }

    @Test
    void sendsWaecRegistrationQuantityOneAndParsesTokenArray() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassPurchaseClient client = new VtpassPurchaseClient(restTemplate);

        configure(client);

        server.expect(requestTo("https://sandbox.vtpass.com/api/pay"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "api-key-value"))
                .andExpect(header("secret-key", "secret-key-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("serviceID=waec-registration"),
                        org.hamcrest.Matchers.containsString("variation_code=waec-registration"),
                        org.hamcrest.Matchers.containsString("quantity=1"),
                        org.hamcrest.Matchers.containsString("phone=08012345678")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{
                            "transactions":{
                              "status":"delivered",
                              "transactionId":"REG-20260914-001"
                            }
                          },
                          "response_description":"TRANSACTION SUCCESSFUL",
                          "tokens":["WAEC-REG-TOKEN-123456"]
                        }
                        """, MediaType.APPLICATION_JSON));

        ProviderResult result = client.purchase(
                "EDUCATION",
                "waec-registration",
                "waec-registration",
                BigDecimal.valueOf(20000),
                "08012345678",
                "08012345678",
                "quantity:1",
                "202609141530waecreg0001"
        );

        assertEquals(ProviderOutcome.SUCCESS, result.outcome());
        assertEquals("REG-20260914-001", result.transactionId());
        assertEquals("Token: WAEC-REG-TOKEN-123456", result.fulfillment());
        server.verify();
    }

    private void configure(VtpassPurchaseClient client) {
        ReflectionTestUtils.setField(client, "baseUrl", "https://sandbox.vtpass.com/api/");
        ReflectionTestUtils.setField(client, "apiKey", "api-key-value");
        ReflectionTestUtils.setField(client, "secretKey", "secret-key-value");
    }
}
