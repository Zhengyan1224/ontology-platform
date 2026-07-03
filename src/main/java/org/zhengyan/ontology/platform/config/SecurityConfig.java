package org.zhengyan.ontology.platform.config;

import jakarta.servlet.http.HttpServletResponse;
import org.zhengyan.ontology.platform.service.ApiKeyFilter;
import org.zhengyan.ontology.platform.service.ApiKeyService;
import org.zhengyan.ontology.platform.service.AuditService;
import org.zhengyan.ontology.platform.service.JwtAuthFilter;
import org.zhengyan.ontology.platform.service.JwtService;
import org.zhengyan.ontology.platform.service.RateLimitFilter;
import org.zhengyan.ontology.platform.repository.JwtBlacklistRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.session.SessionManagementFilter;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@ConditionalOnProperty(name = "ontology.auth.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    public ApiKeyFilter apiKeyFilter(ApiKeyService apiKeyService, AuditService auditService) {
        return new ApiKeyFilter(apiKeyService, auditService, List.of(
                "/api/v1/health",
                "/api/v1/auth/",
                "/swagger-ui/",
                "/v3/api-docs/",
                "/h2-console/",
                "/ontology-viz/"
        ));
    }

    @Bean
    public JwtAuthFilter jwtAuthFilter(JwtService jwtService, JwtBlacklistRepository jwtBlacklistRepository, AuditService auditService) {
        return new JwtAuthFilter(jwtService, jwtBlacklistRepository, auditService);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(
            @Value("${ontology.rate-limit.capacity}") long capacity,
            @Value("${ontology.rate-limit.refill-tokens}") long refillTokens,
            @Value("${ontology.rate-limit.refill-period-seconds}") long refillPeriodSeconds,
            AuditService auditService) {
        return new RateLimitFilter(capacity, refillTokens, refillPeriodSeconds, auditService);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           ApiKeyFilter apiKeyFilter,
                                           JwtAuthFilter jwtAuthFilter,
                                           RateLimitFilter rateLimitFilter) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/api/v1/health",
                    "/api/v1/auth/**",
                    "/swagger-ui/**",
                    "/v3/api-docs/**",
                    "/h2-console/**",
                    "/ontology-viz/**"
                ).permitAll()
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write("{\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
                })
            )
            .addFilterBefore(rateLimitFilter, SessionManagementFilter.class)
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
