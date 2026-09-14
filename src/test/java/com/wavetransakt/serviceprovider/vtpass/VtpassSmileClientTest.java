package com.wavetransakt.serviceprovider.vtpass;

import com.wavetransakt.serviceprovider.dto.SmileServiceDtos.VerifyEmailResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VtpassSmileClientTest {

    @Test
    void verifiesEmailAndParsesReturnedAccounts() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        VtpassSmileClient client = new VtpassSmileClient(restTemplate);

        ReflectionTestUtils.setField(client, "baseUrl", "https://sandbox.vtpass.com/api/");
        ReflectionTestUtils.setField(client, "apiKey", "api-key-value");
        ReflectionTestUtils.setField(client, "secretKey", "secret-key-value");

        server.expect(requestTo("https://sandbox.vtpass.com/api/merchant-verify/smile/email"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "api-key-value"))
                .andExpect(header("secret-key", "secret-key-value"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("billersCode=tester%40sandbox.com"),
                        org.hamcrest.Matchers.containsString("serviceID=smile-direct")
                )))
                .andRespond(withSuccess("""
                        {
                          "code":"000",
                          "content":{
                            "Customer_Name":"THE TESTER ITSELF",
                            "AccountList":{
                              "Account":[
                                {"AccountId":"08011111111","FriendlyName":"TESTER1"},
                                {"AccountId":"08022222222","FriendlyName":"TESTER2"}
                              ],
                              "NumberOfAccounts":2
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        VerifyEmailResponse response = client.verifyEmail("Tester@Sandbox.com");

        assertTrue(response.valid());
        assertEquals("tester@sandbox.com", response.email());
        assertEquals("THE TESTER ITSELF", response.customerName());
        assertEquals(2, response.accounts().size());
        assertEquals("08011111111", response.accounts().getFirst().accountId());
        assertEquals("TESTER1", response.accounts().getFirst().friendlyName());
        assertTrue(client.containsAccount(response, "08022222222"));
        server.verify();
    }
}
