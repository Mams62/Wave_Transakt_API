package com.wavetransakt.security;

import com.wavetransakt.user.entity.User;
import com.wavetransakt.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authorizationHeader =
                request.getHeader("Authorization");

        // No JWT supplied
        if (authorizationHeader == null ||
                !authorizationHeader.startsWith("Bearer ")) {

            filterChain.doFilter(request, response);
            return;
        }

        String token =
                authorizationHeader.substring(7).trim();

        try {

            // Validate token
            if (!jwtService.isTokenValid(token)) {

                System.out.println(
                        "JWT DEBUG: Token is invalid"
                );

                filterChain.doFilter(request, response);
                return;
            }

            // Extract user ID
            String userId =
                    jwtService.extractUserId(token);

            System.out.println(
                    "JWT DEBUG: User ID = " + userId
            );

            UUID uuid =
                    UUID.fromString(userId);

            // Find user
            User user =
                    userRepository.findById(uuid)
                            .orElse(null);

            if (user == null) {

                System.out.println(
                        "JWT DEBUG: User not found"
                );

                filterChain.doFilter(request, response);
                return;
            }

            // Don't overwrite an existing authentication
            if (SecurityContextHolder
                    .getContext()
                    .getAuthentication() == null) {

                var authorities = List.of(
                        new SimpleGrantedAuthority(
                                "ROLE_" +
                                        user.getRole().name()
                        )
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,
                                null,
                                authorities
                        );

                SecurityContextHolder
                        .getContext()
                        .setAuthentication(authentication);

                System.out.println(
                        "JWT DEBUG: Authentication set for "
                                + user.getEmail()
                );
            }

        } catch (Exception e) {

            System.out.println(
                    "JWT DEBUG: Authentication failed: "
                            + e.getMessage()
            );

            e.printStackTrace();
        }

        filterChain.doFilter(request, response);
    }
}