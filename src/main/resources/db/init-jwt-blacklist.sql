CREATE TABLE IF NOT EXISTS jwt_blacklist (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    jti VARCHAR(255) NOT NULL UNIQUE,
    token_subject VARCHAR(255),
    reason VARCHAR(50),
    expired_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_jwt_blacklist_jti ON jwt_blacklist(jti);
CREATE INDEX IF NOT EXISTS idx_jwt_blacklist_expired_at ON jwt_blacklist(expired_at);
