package org.zhengyan.ontology.platform.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.config.AuthProperties;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "ontology.auth.enabled", havingValue = "true", matchIfMissing = true)
public class JwtService {

    private final SecretKey secretKey;
    private final long expirationMs;

    public JwtService(AuthProperties authProperties) {
        String secret = authProperties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("ontology.auth.jwt.secret must be configured");
        }
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = authProperties.getJwt().getExpirationMs();
    }

    public String generateToken(String username, String role) {
        return generateToken(username, role, UUID.randomUUID().toString());
    }

    public String generateToken(String username, String role, String jti) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .id(jti)
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractTokenId(String token) {
        return parseClaims(token).getId();
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public String extractRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> extractAuthorities(String token) {
        String role = extractRole(token);
        if (role == null || role.isBlank()) {
            return List.of();
        }
        return List.of(role);
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
