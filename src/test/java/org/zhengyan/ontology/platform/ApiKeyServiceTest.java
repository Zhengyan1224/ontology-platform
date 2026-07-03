package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.zhengyan.ontology.platform.model.ApiKeyEntity;
import org.zhengyan.ontology.platform.repository.ApiKeyRepository;
import org.zhengyan.ontology.platform.service.ApiKeyService;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class ApiKeyServiceTest {

    private ApiKeyRepository apiKeyRepository;
    private ApiKeyService apiKeyService;

    @BeforeEach
    void setUp() {
        apiKeyRepository = mock(ApiKeyRepository.class);
        apiKeyService = new ApiKeyService(apiKeyRepository);
    }

    @Test
    void validateKeyReturnsEmptyForNull() {
        assertTrue(apiKeyService.validateKey(null).isEmpty());
        assertTrue(apiKeyService.validateKey("").isEmpty());
        assertTrue(apiKeyService.validateKey("   ").isEmpty());
    }

    @Test
    void validateKeyHitsRepositoryOnFirstCall() {
        String rawKey = "ont-test-key-123";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKeyEntity entity = createKeyEntity(1L, hash, "test-key", "ROLE_ADMIN", "*");

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(entity));

        Optional<ApiKeyEntity> result = apiKeyService.validateKey(rawKey);

        assertTrue(result.isPresent());
        assertEquals("test-key", result.get().getName());
        verify(apiKeyRepository).findByKeyHash(hash);
    }

    @Test
    void validateKeyUsesCacheOnSecondCall() {
        String rawKey = "ont-cached-key";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKeyEntity entity = createKeyEntity(2L, hash, "cached-key", "ROLE_ADMIN", "*");

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(entity));

        apiKeyService.validateKey(rawKey);
        apiKeyService.validateKey(rawKey);

        verify(apiKeyRepository, times(1)).findByKeyHash(hash);
    }

    @Test
    void toggleKeyUpdatesEnabledState() {
        when(apiKeyRepository.updateEnabled(1L, false)).thenReturn(1);

        boolean result = apiKeyService.toggleKey(1L, false);

        assertTrue(result);
        verify(apiKeyRepository).updateEnabled(1L, false);
    }

    @Test
    void deleteKeyInvalidatesCache() {
        String rawKey = "ont-delete-me";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKeyEntity entity = createKeyEntity(3L, hash, "delete-key", "ROLE_ADMIN", "*");

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(entity));
        when(apiKeyRepository.deleteById(3L)).thenReturn(1);

        apiKeyService.validateKey(rawKey);
        apiKeyService.deleteKey(3L);
        apiKeyService.validateKey(rawKey);

        verify(apiKeyRepository, times(2)).findByKeyHash(hash);
    }

    @Test
    void expiredKeyNotReturned() {
        String rawKey = "ont-expired";
        String hash = ApiKeyService.sha256(rawKey);
        ApiKeyEntity entity = createKeyEntity(4L, hash, "expired-key", "ROLE_ADMIN", "*");
        entity.setExpiresAt(LocalDateTime.now().minus(Duration.ofMinutes(5)));

        when(apiKeyRepository.findByKeyHash(hash)).thenReturn(Optional.of(entity));

        Optional<ApiKeyEntity> result = apiKeyService.validateKey(rawKey);

        assertTrue(result.isEmpty());
    }

    @Test
    void generateKeyCreatesValidKey() {
        when(apiKeyRepository.save(any())).thenReturn(1);

        String rawKey = apiKeyService.generateKey("new-key", "ROLE_USER", "tenant1,tenant2", LocalDateTime.now().plusDays(30));

        assertNotNull(rawKey);
        assertTrue(rawKey.startsWith("ont-"));
        verify(apiKeyRepository).save(any());
    }

    @Test
    void generateKeyDefaultsTenantScopes() {
        when(apiKeyRepository.save(any())).thenReturn(1);

        String rawKey = apiKeyService.generateKey("default-key", "ROLE_USER", LocalDateTime.now().plusDays(30));

        assertNotNull(rawKey);
        verify(apiKeyRepository).save(argThat(e -> "*".equals(e.getTenantScopes())));
    }

    @Test
    void seedKeyDoesNotDuplicate() {
        String rawKey = "ont-seed-key";
        String hash = ApiKeyService.sha256(rawKey);

        when(apiKeyRepository.existsByKeyHash(hash)).thenReturn(true);

        apiKeyService.seedKey(rawKey, "seed", "ROLE_USER");

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void listKeysDelegatesToRepository() {
        when(apiKeyRepository.findAll()).thenReturn(List.of());

        apiKeyService.listKeys();

        verify(apiKeyRepository).findAll();
    }

    private ApiKeyEntity createKeyEntity(Long id, String hash, String name, String role, String tenantScopes) {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(id);
        entity.setKeyHash(hash);
        entity.setKeyPrefix(hash.substring(0, 8) + "...");
        entity.setName(name);
        entity.setRole(role);
        entity.setTenantScopes(tenantScopes);
        entity.setEnabled(true);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return entity;
    }
}
