CREATE TABLE workflow_definition (
    id VARCHAR(128) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    current_version VARCHAR(32),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE workflow_version (
    workflow_id VARCHAR(128) NOT NULL REFERENCES workflow_definition (id) ON DELETE CASCADE,
    version VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'draft',
    model_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (workflow_id, version)
);

CREATE TABLE platform_id_registry (
    id VARCHAR(160) PRIMARY KEY,
    kind VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_workflow_definition_status ON workflow_definition (status);
CREATE INDEX idx_workflow_definition_updated_at ON workflow_definition (updated_at DESC);
CREATE INDEX idx_platform_id_registry_kind ON platform_id_registry (kind);
