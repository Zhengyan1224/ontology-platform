CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(100),
    query_type VARCHAR(20) NOT NULL,
    query_text TEXT,
    generated_sparql TEXT,
    translated_sql TEXT,
    duration_ms BIGINT,
    success BOOLEAN,
    error_message TEXT,
    result_count INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_id ON audit_logs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_query_type ON audit_logs(query_type);
CREATE INDEX IF NOT EXISTS idx_audit_logs_created_at ON audit_logs(created_at);
