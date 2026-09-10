package com.wavetransakt.config;

import com.wavetransakt.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter
            jwtAuthenticationFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http
    ) throws Exception {

        http
                .csrf(
                        csrf ->
                                csrf.disable()
                )

                .sessionManagement(
                        session ->
                                session.sessionCreationPolicy(
                                        SessionCreationPolicy.STATELESS
                                )
                )

                .authorizeHttpRequests(
                        auth -> auth

                                /*
                                 * Basic public endpoints.
                                 */
                                .requestMatchers(
                                        "/",
                                        "/error",
                                        "/api/health"
                                )
                                .permitAll()

                                /*
                                 * Paystack cannot send a Wave Transakt JWT.
                                 *
                                 * Authentication for this ONE endpoint
                                 * is performed using Paystack's HMAC-SHA512
                                 * webhook signature.
                                 */
                                .requestMatchers(
                                        "/api/v1/payments/webhook/paystack"
                                )
                                .permitAll()

                                /*
                                 * Authenticated profile.
                                 */
                                .requestMatchers(
                                        "/api/auth/me"
                                )
                                .authenticated()

                                /*
                                 * Public authentication endpoints.
                                 */
                                .requestMatchers(
                                        "/api/auth/register",
                                        "/api/auth/login"
                                )
                                .permitAll()

                                /*
                                 * Email verification.
                                 */
                                .requestMatchers(
                                        "/api/verification/**"
                                )
                                .permitAll()

                                /*
                                 * EVERYTHING ELSE requires JWT.
                                 */
                                .anyRequest()
                                .authenticated()
                )

                .addFilterBefore(
                        jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class
                );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}