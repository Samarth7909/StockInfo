package com.indira.opsconsole.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.*;

import java.util.List;

/**
 * Spring Security configuration.
 *
 * Route rules (enforce in API, not just UI):
 * - POST /api/auth/login     - public
 * - POST /api/imports        - OPS_LEAD only
 * - GET  /api/imports/**     - INVESTIGATOR, OPS_LEAD, AUDITOR
 * - GET  /api/cases/**       - all authenticated roles (scope filtering in service layer)
 * - POST /api/cases/{id}/notes  - INVESTIGATOR, OPS_LEAD
 * - POST /api/cases/{id}/transition - INVESTIGATOR, OPS_LEAD
 * - GET  /api/metrics        - all authenticated roles
 * - GET  /actuator/**        - all authenticated roles
 * - SUPPORT role: read-only, masked data - enforced at service layer per domain rules
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(401);
                    response.getWriter().write("{\"error\":\"Unauthorized\"}");
                })
            )
            .authorizeHttpRequests(auth -> auth
                // Public
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Imports: only OPS_LEAD may ingest
                .requestMatchers(HttpMethod.POST, "/api/imports").hasRole("OPS_LEAD")
                .requestMatchers(HttpMethod.GET,  "/api/imports/**")
                    .hasAnyRole("INVESTIGATOR", "OPS_LEAD", "AUDITOR")

                // Cases: all authenticated roles read; write roles checked in service
                .requestMatchers(HttpMethod.GET, "/api/cases/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/cases/*/notes")
                    .hasAnyRole("INVESTIGATOR", "OPS_LEAD")
                .requestMatchers(HttpMethod.POST, "/api/cases/*/transition")
                    .hasAnyRole("INVESTIGATOR", "OPS_LEAD")

                // Metrics
                .requestMatchers(HttpMethod.GET, "/api/metrics").authenticated()

                // Actuator (non-health)
                .requestMatchers("/actuator/**").authenticated()

                // Everything else requires authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("http://localhost:5173", "http://localhost:3000"));
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cfg);
        return source;
    }
}
