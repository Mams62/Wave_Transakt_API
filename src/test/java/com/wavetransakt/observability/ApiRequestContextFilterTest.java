package com.wavetransakt.observability;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ApiRequestContextFilterTest {

    private final ApiRequestContextFilter filter = new ApiRequestContextFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void validRequestIdIsPropagatedAndMdcIsClearedAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers/bank/secret-reference");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(ApiRequestContextFilter.REQUEST_ID_HEADER, "wave-client-12345");
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/transfers/bank/{reference}"
        );

        FilterChain chain = (req, res) -> {
            assertEquals("wave-client-12345", MDC.get(ApiRequestContextFilter.MDC_REQUEST_ID));
            assertEquals(
                    "wave-client-12345",
                    req.getAttribute(ApiRequestContextFilter.REQUEST_ID_ATTRIBUTE)
            );
            ((MockHttpServletResponse) res).setStatus(202);
        };

        filter.doFilter(request, response, chain);

        assertEquals("wave-client-12345", response.getHeader(ApiRequestContextFilter.REQUEST_ID_HEADER));
        assertNull(MDC.get(ApiRequestContextFilter.MDC_REQUEST_ID));
    }

    @Test
    void unsafeRequestIdIsReplacedWithServerGeneratedUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/profile");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(ApiRequestContextFilter.REQUEST_ID_HEADER, "bad id with spaces and\nnewline");
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/profile");

        filter.doFilter(request, response, (req, res) -> { });

        String generated = response.getHeader(ApiRequestContextFilter.REQUEST_ID_HEADER);
        assertNotNull(generated);
        assertDoesNotThrow(() -> UUID.fromString(generated));
        assertNotEquals("bad id with spaces and\nnewline", generated);
    }

    @Test
    void safeRouteUsesMvcTemplateNotConcreteSensitivePath() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/v1/transfers/bank/provider-reference-123456789"
        );
        request.setAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE,
                "/api/v1/transfers/bank/{reference}"
        );

        assertEquals(
                "/api/v1/transfers/bank/{reference}",
                filter.safeRouteTemplate(request)
        );
    }

    @Test
    void unresolvedRouteDoesNotFallBackToRawRequestUri() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/password/reset/sensitive-token-value"
        );

        assertEquals("UNMATCHED", filter.safeRouteTemplate(request));
    }
}
