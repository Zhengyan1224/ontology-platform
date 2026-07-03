package org.zhengyan.ontology.platform;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.zhengyan.ontology.platform.repository.JwtBlacklistRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class JwtBlacklistRepositoryTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private JwtBlacklistRepository repository;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS jwt_blacklist (" +
                "id BIGINT AUTO_INCREMENT PRIMARY KEY, " +
                "jti VARCHAR(255) NOT NULL UNIQUE, " +
                "token_subject VARCHAR(255), " +
                "reason VARCHAR(50), " +
                "expired_at TIMESTAMP NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
        repository = new JwtBlacklistRepository(jdbcTemplate);
    }

    @AfterEach
    void tearDown() {
        jdbcTemplate.execute("DELETE FROM jwt_blacklist");
    }

    @Test
    void addEntry() {
        repository.add("jti-1", "user1", "LOGOUT", LocalDateTime.now().plusDays(1));
        assertTrue(repository.exists("jti-1"));
    }

    @Test
    void existReturnsFalseForUnknownJti() {
        assertFalse(repository.exists("unknown-jti"));
    }

    @Test
    void revokeAllForSubjectCreatesAllEntry() {
        LocalDateTime expiry = LocalDateTime.now().plusDays(1);
        repository.revokeAllForSubject("user1", expiry);
        assertTrue(repository.isSubjectRevoked("user1"));
    }

    @Test
    void subjectNotRevokedIfNoAllEntry() {
        assertFalse(repository.isSubjectRevoked("nonexistent-user"));
    }

    @Test
    void purgeExpiredRemovesOldEntries() {
        repository.add("jti-expired", "user1", "LOGOUT", LocalDateTime.now().minusMinutes(1));
        repository.purgeExpired();
        assertFalse(repository.exists("jti-expired"));
    }

    @Test
    void purgeExpiredKeepsValidEntries() {
        repository.add("jti-valid", "user1", "LOGOUT", LocalDateTime.now().plusDays(1));
        repository.purgeExpired();
        assertTrue(repository.exists("jti-valid"));
    }

    @Test
    void invalidateAllForSubjectRemovesEntries() {
        repository.add("jti-1", "user1", "LOGOUT", LocalDateTime.now().plusDays(1));
        repository.add("jti-2", "user1", "LOGOUT", LocalDateTime.now().plusDays(1));
        repository.invalidateAllForSubject("user1");
        assertFalse(repository.exists("jti-1"));
        assertFalse(repository.exists("jti-2"));
    }

    @Test
    void existingExpiredEntryReturnsFalseForExistsCheck() {
        repository.add("jti-exp", "user1", "LOGOUT", LocalDateTime.now().minusMinutes(5));
        repository.purgeExpired();
        assertFalse(repository.exists("jti-exp"));
    }
}
