package org.zhengyan.ontology.platform.config;

import org.zhengyan.ontology.platform.service.ApiKeyFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.List;

@Configuration
@EnableWebSecurity
@ConditionalOnProperty(name = "ontology.auth.enabled", havingValue = "true", matchIfMissing = true)
public class SecurityConfig {

    @Bean
    @ConfigurationProperties(prefix = "ontology.auth")
    public AuthProperties authProperties() {
        return new AuthProperties(true, List.of());
    }

    @Bean
    public ApiKeyFilter apiKeyFilter(AuthProperties authProperties) {
        return new ApiKeyFilter(authProperties);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, ApiKeyFilter apiKeyFilter) throws Exception {
        http
            .csrf(csrf -> csrf.ignoringRequestMatchers(
                new MvcRequestMatcher(new HandlerMappingIntrospector(), "/**")
            ))
            .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    new MvcRequestMatcher(new HandlerMappingIntrospector(), "/api/v1/health"),
                    new MvcRequestMatcher(new HandlerMappingIntrospector(), "/swagger-ui/**"),
                    new MvcRequestMatcher(new HandlerMappingIntrospector(), "/v3/api-docs/**"),
                    new MvcRequestMatcher(new HandlerMappingIntrospector(), "/h2-console/**")
                ).permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(apiKeyFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
