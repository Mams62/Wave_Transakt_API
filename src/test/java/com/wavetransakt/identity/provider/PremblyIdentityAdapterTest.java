package com.wavetransakt.identity.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PremblyIdentityAdapterTest {

    private final PremblyGovernmentIdentityClient client =
            new PremblyGovernmentIdentityClient(new ObjectMapper());

    @Test
    void mapsVerifiedBvnWithDocumentedDateFormat() {
        String json = """
                {"status":true,"response_code":"00",
                 "data":{"firstName":"Amina","middleName":"Lola",
                         "lastName":"Bello","dateOfBirth":"01-Jan-2000"}}
                """;
        var record = client.parseResult(json, "BVN");
        assertEquals("Amina", record.firstName());
        assertEquals("Bello", record.lastName());
        assertEquals(LocalDate.of(2000, 1, 1), record.dateOfBirth());
    }

    @Test
    void mapsVerifiedNinWithDocumentedDateFormat() {
        String json = """
                {"status":true,"response_code":"00",
                 "verification":{"status":"VERIFIED","reference":"test-ref"},
                 "data":{"firstname":"AMINA","surname":"BELLO","middlename":"LOLA",
                         "birthdate":"02-05-2001","gender":"f",
                         "nin_suspension_status":false}}
                """;
        var record = client.parseResult(json, "NIN");
        assertEquals(LocalDate.of(2001, 5, 2), record.dateOfBirth());
        assertEquals("f", record.gender());
    }

    @Test
    void neverTreatsProviderErrorAsVerified() {
        assertThrows(IllegalArgumentException.class,
                () -> client.parseResult("{\"status\":false,\"response_code\":\"01\"}", "NIN"));
        assertThrows(IllegalArgumentException.class,
                () -> client.parseResult("{\"status\":true,\"response_code\":\"00\",\"data\":{}}", "BVN"));
    }

    @Test
    void rejectsSuspendedNinAndRejectedVerification() {
        assertThrows(IllegalArgumentException.class, () ->
                client.parseResult("""
                    {"status":true,"response_code":"00","data":{
                      "firstname":"Amina","surname":"Bello","birthdate":"02-05-2001",
                      "nin_suspension_status":true}}
                    """, "NIN"));
        assertThrows(IllegalArgumentException.class, () ->
                client.parseResult("""
                    {"status":true,"response_code":"00",
                     "verification":{"status":"REJECTED"},
                     "data":{"firstname":"Amina","surname":"Bello","birthdate":"02-05-2001"}}
                    """, "NIN"));
    }

    @Test
    void keysMustBePresentAndNeverReadFromTheMobileApp() {
        assertFalse(client.isConfigured());
        ReflectionTestUtils.setField(client, "apiKey", "unit-test-placeholder");
        assertTrue(client.isConfigured());
    }

    @Test
    void routerDoesNotFallBackToDifferentProvider() {
        PremblyGovernmentIdentityClient prembly = mock(PremblyGovernmentIdentityClient.class);
        DojahGovernmentIdentityClient dojah = mock(DojahGovernmentIdentityClient.class);
        GovernmentIdentityProviderRouter router = new GovernmentIdentityProviderRouter(prembly, dojah);

        assertFalse(router.isConfigured());
        assertThrows(IllegalStateException.class, () -> router.lookupBvn("00000000000"));
        verifyNoInteractions(prembly, dojah);

        ReflectionTestUtils.setField(router, "configuredProvider", "PREMBLY");
        when(prembly.isConfigured()).thenReturn(false);
        assertFalse(router.isConfigured());
        verifyNoInteractions(dojah);
    }
}
