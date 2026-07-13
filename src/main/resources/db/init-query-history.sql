CREATE TABLE IF NOT EXISTS query_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id VARCHAR(255) NOT NULL,
    api_key_id BIGINT,
    sparql CLOB,
    execution_time_ms BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_query_history_api_key ON query_history(api_key_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_query_history_tenant ON query_history(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_query_history_created ON query_history(created_at);
