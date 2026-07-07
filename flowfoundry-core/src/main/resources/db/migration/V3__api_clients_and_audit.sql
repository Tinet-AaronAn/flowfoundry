CREATE TABLE platform_api_client (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    status VARCHAR(16) NOT NULL DEFAULT 'active',
    admin_flag BOOLEAN NOT NULL DEFAULT FALSE,
    key_hash VARCHAR(64) NOT NULL,
    key_prefix VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMP
);

CREATE UNIQUE INDEX idx_platform_api_client_key_hash ON platform_api_client (key_hash);
CREATE INDEX idx_platform_api_client_status ON platform_api_client (status);

CREATE TABLE platform_api_client_namespace (
    client_id VARCHAR(64) NOT NULL REFERENCES platform_api_client (id) ON DELETE CASCADE,
    namespace VARCHAR(64) NOT NULL,
    PRIMARY KEY (client_id, namespace)
);

CREATE TABLE platform_audit_log (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    client_id VARCHAR(64),
    actor_client_id VARCHAR(64),
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(255),
    namespace VARCHAR(64),
    http_method VARCHAR(16),
    path VARCHAR(512),
    status_code INTEGER,
    detail TEXT,
    ip_address VARCHAR(64)
);

CREATE INDEX idx_platform_audit_log_occurred_at ON platform_audit_log (occurred_at DESC);
CREATE INDEX idx_platform_audit_log_client_id ON platform_audit_log (client_id);
CREATE INDEX idx_platform_audit_log_action ON platform_audit_log (action);
