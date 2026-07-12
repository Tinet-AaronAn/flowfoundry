CREATE TABLE platform_plugin (
    id VARCHAR(64) NOT NULL,
    version VARCHAR(32) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    namespace VARCHAR(64) NOT NULL,
    task_queue VARCHAR(255) NOT NULL,
    typed_workflows BOOLEAN NOT NULL DEFAULT FALSE,
    state VARCHAR(32) NOT NULL,
    desired_state VARCHAR(32) NOT NULL,
    replicas INT NOT NULL DEFAULT 1,
    jar_path VARCHAR(512) NOT NULL,
    jar_sha256 VARCHAR(64) NOT NULL,
    error_detail TEXT,
    runtime_ref VARCHAR(255),
    uploaded_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, version),
    CONSTRAINT fk_platform_plugin_namespace
        FOREIGN KEY (namespace) REFERENCES platform_namespace (id)
);

CREATE INDEX idx_platform_plugin_namespace ON platform_plugin (namespace);
