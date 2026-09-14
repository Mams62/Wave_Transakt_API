package com.wavetransakt.serviceprovider.vtpass;

import com.wavetransakt.serviceprovider.dto.ServiceVerificationDtos.VerifyResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VtpassVerificationClientTest {

    @Test
    void parsesVerifiedElectricityCustomerAndUsesPostCredentials() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassVerificationClient client = configuredClient(restTemplate);

        server.expect(requestTo("https://sandbox.vtpass.com/api/merchant-verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "api-key-value"))
                .andExpect(header("secret-key", "secret-key-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("billersCode=12345678901"),
                        org.hamcrest.Matchers.containsString("serviceID=abuja-electric"),
                        org.hamcrest.Matchers.containsString("type=prepaid")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{
                            "Customer_Name":"Wave Test Customer",
                            "Address":"Abuja",
                            "Meter_Type":"PREPAID",
                            "Min_Purchase_Amount":"100.00",
                            "WrongBillersCode":false
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VerifyResponse response = client.verify(
                "ELECTRICITY",
                "abuja-electric",
                "12345678901",
                "prepaid"
        );

        assertTrue(response.valid());
        assertEquals("Wave Test Customer", response.customerName());
        assertEquals("PREPAID", response.accountType());
        assertEquals("100.00", response.minimumAmount().toPlainString());
        server.verify();
    }

    @Test
    void verifiesJambProfileIdUsingSelectedVariationAsType() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassVerificationClient client = configuredClient(restTemplate);

        server.expect(requestTo("https://sandbox.vtpass.com/api/merchant-verify"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "api-key-value"))
                .andExpect(header("secret-key", "secret-key-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("billersCode=0123456789"),
                        org.hamcrest.Matchers.containsString("serviceID=jamb"),
                        org.hamcrest.Matchers.containsString("type=utme-mock")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{
                            "Customer_Name":"Capital James"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VerifyResponse response = client.verify(
                "EDUCATION",
                "jamb",
                "0123456789",
                "utme-mock"
        );

        assertTrue(response.valid());
        assertEquals("jamb", response.serviceId());
        assertEquals("Capital James", response.customerName());
        assertEquals("JAMB Profile ID verified", response.message());
        server.verify();
    }

    private VtpassVerificationClient configuredClient(RestTemplate restTemplate) {
        VtpassVerificationClient client = new VtpassVerificationClient(restTemplate);
        ReflectionTestUtils.setField(client, "baseUrl", "https://sandbox.vtpass.com/api/");
        ReflectionTestUtils.setField(client, "apiKey", "api-key-value");
        ReflectionTestUtils.setField(client, "secretKey", "secret-key-value");
        return client;
    }
}
