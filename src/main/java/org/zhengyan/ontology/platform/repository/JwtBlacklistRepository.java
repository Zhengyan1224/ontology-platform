package org.zhengyan.ontology.platform.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Repository
/**
 * @author 郑炎 Zheng Yan
 */
public class JwtBlacklistRepository {

    private static final Logger log = LoggerFactory.getLogger(JwtBlacklistRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public JwtBlacklistRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void add(String jti, String subject, String reason, LocalDateTime expiredAt) {
        if (exists(jti)) {
            jdbcTemplate.update(
                    "UPDATE jwt_blacklist SET token_subject = ?, reason = ?, expired_at = ?, created_at = ? WHERE jti = ?",
                    subject, reason, Timestamp.valueOf(expiredAt), Timestamp.valueOf(LocalDateTime.now()), jti);
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO jwt_blacklist (jti, token_subject, reason, expired_at, created_at) VALUES (?, ?, ?, ?, ?)",
                jti, subject, reason, Timestamp.valueOf(expiredAt), Timestamp.valueOf(LocalDateTime.now()));
    }

    public boolean exists(String jti) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_blacklist WHERE jti = ?", Integer.class, jti);
        return count != null && count > 0;
    }

    public boolean isSubjectRevoked(String subject) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_blacklist WHERE jti = 'ALL' AND token_subject = ?", Integer.class, subject);
        return count != null && count > 0;
    }

    public void revokeAllForSubject(String subject, LocalDateTime expiredAt) {
        if (exists("ALL")) {
            jdbcTemplate.update(
                    "UPDATE jwt_blacklist SET token_subject = ?, reason = ?, expired_at = ?, created_at = ? WHERE jti = 'ALL'",
                    subject, "REVOKE_ALL", Timestamp.valueOf(expiredAt), Timestamp.valueOf(LocalDateTime.now()));
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO jwt_blacklist (jti, token_subject, reason, expired_at, created_at) VALUES ('ALL', ?, ?, ?, ?)",
                subject, "REVOKE_ALL", Timestamp.valueOf(expiredAt), Timestamp.valueOf(LocalDateTime.now()));
    }

    public void invalidateAllForSubject(String subject) {
        jdbcTemplate.update(
                "DELETE FROM jwt_blacklist WHERE token_subject = ?", subject);
    }

    @Scheduled(fixedRateString = "${ontology.jwt.blacklist.cleanup-interval:300000}")
    public void purgeExpired() {
        int deleted = jdbcTemplate.update(
                "DELETE FROM jwt_blacklist WHERE expired_at < ?",
                Timestamp.valueOf(LocalDateTime.now()));
        if (deleted > 0) {
            log.info("Purged {} expired JWT blacklist entries", deleted);
        }
    }
}
