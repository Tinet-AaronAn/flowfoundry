CREATE TABLE temporal_cluster (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    host VARCHAR(255) NOT NULL,
    ui_base_url VARCHAR(512),
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE platform_namespace
    ADD COLUMN temporal_cluster_id VARCHAR(64);

ALTER TABLE platform_namespace
    ADD CONSTRAINT fk_platform_namespace_temporal_cluster
        FOREIGN KEY (temporal_cluster_id) REFERENCES temporal_cluster (id);

CREATE INDEX idx_platform_namespace_temporal_cluster ON platform_namespace (temporal_cluster_id);
