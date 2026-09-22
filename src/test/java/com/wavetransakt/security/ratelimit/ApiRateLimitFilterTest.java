package com.wavetransakt.security.ratelimit;

import com.wavetransakt.user.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.ForwardedHeaderFilter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApiRateLimitFilterTest {

    @Mock
    DistributedRateLimitService rateLimitService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginUsesSourceScopedPolicy() throws Exception {
        when(rateLimitService.consume(
                eq("AUTH_LOGIN_SOURCE"),
                eq("SOURCE:203.0.113.10"),
                eq(60),
                eq(Duration.ofMinutes(5))
        )).thenReturn(allowed(60, 59));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.10");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        assertEquals("60", response.getHeader("X-RateLimit-Limit"));
        assertEquals("59", response.getHeader("X-RateLimit-Remaining"));
    }

    @Test
    void rawForwardedForHeaderIsNeverParsedDirectlyByLimiter() throws Exception {
        when(rateLimitService.consume(
                eq("AUTH_LOGIN_SOURCE"),
                eq("SOURCE:10.0.0.7"),
                eq(60),
                eq(Duration.ofMinutes(5))
        )).thenReturn(allowed(60, 59));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "198.51.100.55");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> { });

        verify(rateLimitService).consume(
                "AUTH_LOGIN_SOURCE",
                "SOURCE:10.0.0.7",
                60,
                Duration.ofMinutes(5)
        );
        verify(rateLimitService, never()).consume(
                eq("AUTH_LOGIN_SOURCE"),
                eq("SOURCE:198.51.100.55"),
                anyInt(),
                any(Duration.class)
        );
    }

    @Test
    void springForwardedHeaderNormalizationFeedsClientAddressToLimiter() throws Exception {
        when(rateLimitService.consume(
                eq("AUTH_LOGIN_SOURCE"),
                eq("SOURCE:198.51.100.55"),
                eq(60),
                eq(Duration.ofMinutes(5))
        )).thenReturn(allowed(60, 59));

        ApiRateLimitFilter apiFilter = new ApiRateLimitFilter(rateLimitService, true);
        ForwardedHeaderFilter forwardedHeaderFilter = new ForwardedHeaderFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("10.0.0.7");
        request.addHeader("X-Forwarded-For", "198.51.100.55");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        forwardedHeaderFilter.doFilter(
                request,
                response,
                (forwardedRequest, forwardedResponse) -> apiFilter.doFilter(
                        forwardedRequest,
                        forwardedResponse,
                        (req, res) -> invoked.set(true)
                )
        );

        assertTrue(invoked.get());
        verify(rateLimitService).consume(
                "AUTH_LOGIN_SOURCE",
                "SOURCE:198.51.100.55",
                60,
                Duration.ofMinutes(5)
        );
    }

    @Test
    void posPairingRedeemUsesSourceScopedPolicy() throws Exception {
        when(rateLimitService.consume(
                eq("POS_PAIRING_REDEEM_SOURCE"),
                eq("SOURCE:203.0.113.20"),
                eq(30),
                eq(Duration.ofMinutes(5))
        )).thenReturn(allowed(30, 29));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/pos/pairing/redeem");
        request.setRemoteAddr("203.0.113.20");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        verify(rateLimitService).consume(
                "POS_PAIRING_REDEEM_SOURCE",
                "SOURCE:203.0.113.20",
                30,
                Duration.ofMinutes(5)
        );
    }

    @Test
    void authenticatedFinancialWriteUsesUserScopedPolicy() throws Exception {
        UUID userId = UUID.randomUUID();
        User user = User.builder().id(userId).build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of())
        );

        when(rateLimitService.consume(
                eq("BANK_TRANSFER_USER"),
                eq("USER:" + userId),
                eq(10),
                eq(Duration.ofMinutes(1))
        )).thenReturn(allowed(10, 9));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers/bank");
        request.setRemoteAddr("203.0.113.11");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        verify(rateLimitService).consume(
                "BANK_TRANSFER_USER",
                "USER:" + userId,
                10,
                Duration.ofMinutes(1)
        );
    }

    @Test
    void deniedRequestReturns429AndDoesNotReachControllerChain() throws Exception {
        when(rateLimitService.consume(
                anyString(), anyString(), anyInt(), any(Duration.class)
        )).thenReturn(new DistributedRateLimitService.RateLimitDecision(
                false,
                60,
                0,
                42,
                Instant.now().plusSeconds(42)
        ));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr("203.0.113.12");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(429, response.getStatus());
        assertEquals("42", response.getHeader("Retry-After"));
        assertEquals("no-store", response.getHeader("Cache-Control"));
        assertTrue(response.getContentAsString().contains("RATE_LIMITED"));
        assertFalse(response.getContentAsString().contains("203.0.113.12"));
    }

    @Test
    void limiterStorageFailureFailsClosedWith503AndDoesNotExposeRawUri() throws Exception {
        when(rateLimitService.consume(
                anyString(), anyString(), anyInt(), any(Duration.class)
        )).thenThrow(new IllegalStateException("database unavailable"));

        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        String rawSensitiveUri = "/api/v1/transfers/bank";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", rawSensitiveUri);
        request.setRemoteAddr("203.0.113.13");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertFalse(invoked.get());
        assertEquals(503, response.getStatus());
        assertTrue(response.getContentAsString().contains("SECURITY_THROTTLE_UNAVAILABLE"));
        assertFalse(response.getContentAsString().contains(rawSensitiveUri));
        assertFalse(response.getContentAsString().contains("203.0.113.13"));
    }

    @Test
    void ordinaryReadRouteIsNotThrottled() throws Exception {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/transactions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertTrue(invoked.get());
        verifyNoInteractions(rateLimitService);
    }

    @Test
    void routeMappingKeepsProviderWebhooksOutOfGenericThrottle() {
        ApiRateLimitFilter filter = new ApiRateLimitFilter(rateLimitService, true);

        assertNull(filter.ruleFor("POST", "/api/v1/identity/liveness/webhook"));
        assertNull(filter.ruleFor("POST", "/api/payment/webhook/paystack"));
        assertNotNull(filter.ruleFor("GET", "/api/v1/transfers/bank/resolve"));
        assertNotNull(filter.ruleFor("GET", "/api/v1/qr/wallet/WT1234567890"));
    }

    private DistributedRateLimitService.RateLimitDecision allowed(int limit, int remaining) {
        return new DistributedRateLimitService.RateLimitDecision(
                true,
                limit,
                remaining,
                0,
                Instant.now().plusSeconds(60)
        );
    }
}
