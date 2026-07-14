CREATE TABLE IF NOT EXISTS tenant_content (
    tenant_id VARCHAR(100) PRIMARY KEY,
    owl_content CLOB,
    obda_content CLOB,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
