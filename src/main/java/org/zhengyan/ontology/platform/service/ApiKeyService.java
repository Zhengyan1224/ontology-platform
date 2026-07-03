package org.zhengyan.ontology.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.zhengyan.ontology.platform.exception.OntologyPlatformException;
import org.zhengyan.ontology.platform.model.ApiKeyEntity;
import org.zhengyan.ontology.platform.repository.ApiKeyRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final int KEY_BYTE_LENGTH = 32;
    private static final int PREFIX_LENGTH = 8;

    private final ApiKeyRepository apiKeyRepository;
    private final Cache<String, ApiKeyEntity> cache;

    public ApiKeyService(ApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build();
    }

    public Optional<ApiKeyEntity> validateKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        ApiKeyEntity cached = cache.getIfPresent(rawKey);
        if (cached != null) {
            if (cached.getExpiresAt() != null && cached.getExpiresAt().isBefore(LocalDateTime.now())) {
                cache.invalidate(rawKey);
                return Optional.empty();
            }
            apiKeyRepository.updateLastUsedAt(cached.getId(), LocalDateTime.now());
            return Optional.of(cached);
        }
        String hash = sha256(rawKey);
        Optional<ApiKeyEntity> result = apiKeyRepository.findByKeyHash(hash);
        result.ifPresent(key -> {
            if (key.getExpiresAt() != null && key.getExpiresAt().isBefore(LocalDateTime.now())) {
                return;
            }
            cache.put(rawKey, key);
            apiKeyRepository.updateLastUsedAt(key.getId(), LocalDateTime.now());
        });
        return result;
    }

    public String generateKey(String name, String role, LocalDateTime expiresAt) {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[KEY_BYTE_LENGTH];
        random.nextBytes(bytes);
        String rawKey = "ont-" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = sha256(rawKey);
        String prefix = rawKey.substring(0, PREFIX_LENGTH) + "...";

        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyHash(hash);
        entity.setKeyPrefix(prefix);
        entity.setName(name);
        entity.setRole(role);
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        entity.setExpiresAt(expiresAt);

        apiKeyRepository.save(entity);
        log.info("Generated API key '{}' with role {}", name, role);
        return rawKey;
    }

    public void seedKey(String rawKey, String name, String role) {
        String hash = sha256(rawKey);
        if (apiKeyRepository.existsByKeyHash(hash)) {
            return;
        }
        String prefix = rawKey.length() >= PREFIX_LENGTH
                ? rawKey.substring(0, PREFIX_LENGTH) + "..."
                : rawKey;
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setKeyHash(hash);
        entity.setKeyPrefix(prefix);
        entity.setName(name);
        entity.setRole(role);
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        apiKeyRepository.save(entity);
        log.info("Seeded API key '{}' with role {}", name, role);
    }

    public List<ApiKeyEntity> listKeys() {
        return apiKeyRepository.findAll();
    }

    public boolean toggleKey(Long id, boolean enabled) {
        return apiKeyRepository.updateEnabled(id, enabled) > 0;
    }

    public boolean deleteKey(Long id) {
        cache.invalidateAll();
        return apiKeyRepository.deleteById(id) > 0;
    }

    public void invalidateCache() {
        cache.invalidateAll();
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new OntologyPlatformException("SHA-256 not available", 500, "HASH_ERROR", e);
        }
    }
}
