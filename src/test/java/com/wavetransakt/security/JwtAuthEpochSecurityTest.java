package com.wavetransakt.security;

import com.wavetransakt.user.entity.AccountStatus;
import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.entity.UserRole;
import com.wavetransakt.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthEpochSecurityTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void newlyIssuedTokenCarriesAuthenticationEpoch() {
        JwtService jwtService = jwtService();
        UUID userId = UUID.randomUUID();

        String token = jwtService.generateToken(userId, "person@example.com", 7L);

        assertTrue(jwtService.isTokenValid(token));
        assertEquals(userId.toString(), jwtService.extractUserId(token));
        assertEquals(7L, jwtService.extractAuthVersion(token));
        assertEquals(JwtService.ACCESS_FULL, jwtService.extractAccess(token));
    }

    @Test
    void legacyTokenWithoutEpochIsVersionZeroForRolloutCompatibility() {
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        String legacyToken = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .claim("email", "person@example.com")
                .claim("access", JwtService.ACCESS_FULL)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + 60_000L))
                .signWith(key)
                .compact();

        assertEquals(0L, jwtService().extractAuthVersion(legacyToken));
    }

    @Test
    void authenticationFilterRejectsTokenFromOlderEpoch() throws Exception {
        JwtService jwtService = jwtService();
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, 2L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String staleToken = jwtService.generateToken(userId, user.getEmail(), 1L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + staleToken);
        filter.doFilterInternal(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticationFilterAcceptsCurrentEpochToken() throws Exception {
        JwtService jwtService = jwtService();
        UserRepository userRepository = mock(UserRepository.class);
        UUID userId = UUID.randomUUID();
        User user = activeUser(userId, 3L);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        String token = jwtService.generateToken(userId, user.getEmail(), 3L);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userRepository);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilterInternal(
                request,
                new MockHttpServletResponse(),
                new MockFilterChain()
        );

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertSame(user, SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    private JwtService jwtService() {
        return new JwtService(SECRET, 60_000L, 30_000L);
    }

    private User activeUser(UUID id, long authVersion) {
        return User.builder()
                .id(id)
                .firstName("Wave")
                .lastName("User")
                .email("person@example.com")
                .phone("08000000000")
                .password("hash")
                .authVersion(authVersion)
                .role(UserRole.USER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }
}
