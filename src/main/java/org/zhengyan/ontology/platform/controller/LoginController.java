package org.zhengyan.ontology.platform.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.zhengyan.ontology.platform.config.AuthProperties;
import org.zhengyan.ontology.platform.repository.JwtBlacklistRepository;
import org.zhengyan.ontology.platform.service.JwtService;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@ConditionalOnProperty(name = "ontology.auth.enabled", havingValue = "true", matchIfMissing = true)
public class LoginController {

    private final JwtService jwtService;
    private final AuthProperties authProperties;
    private final JwtBlacklistRepository jwtBlacklistRepository;

    public LoginController(JwtService jwtService, AuthProperties authProperties,
                           JwtBlacklistRepository jwtBlacklistRepository) {
        this.jwtService = jwtService;
        this.authProperties = authProperties;
        this.jwtBlacklistRepository = jwtBlacklistRepository;
    }

    @PostMapping("/revoke-all")
    public ResponseEntity<Map<String, Object>> revokeAll(@RequestBody RevokeAllRequest request) {
        jwtBlacklistRepository.revokeAllForSubject(
                request.getUsername(),
                LocalDateTime.now().plusDays(1));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "revoked");
        result.put("subject", request.getUsername());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String adminPassword = authProperties.getAdminPassword();

        if (adminPassword == null || adminPassword.isBlank()) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "LOGIN_DISABLED");
            err.put("message", "Admin login is not configured");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(err);
        }

        if (!"admin".equals(request.getUsername()) || !adminPassword.equals(request.getPassword())) {
            Map<String, String> err = new LinkedHashMap<>();
            err.put("error", "INVALID_CREDENTIALS");
            err.put("message", "Invalid username or password");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(err);
        }

        String token = jwtService.generateToken("admin", "ROLE_ADMIN");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("token", token);
        result.put("type", "Bearer");
        result.put("role", "ROLE_ADMIN");
        return ResponseEntity.ok(result);
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class RevokeAllRequest {
        private String username;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
    }
}
