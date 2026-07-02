CREATE TABLE IF NOT EXISTS tenants (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    jdbc_url VARCHAR(500) NOT NULL,
    jdbc_driver VARCHAR(255) NOT NULL,
    jdbc_username VARCHAR(100),
    jdbc_password VARCHAR(100),
    owl_path VARCHAR(500),
    obda_path VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
