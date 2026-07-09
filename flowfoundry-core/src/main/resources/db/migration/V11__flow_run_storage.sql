CREATE TABLE flow_run (
    workflow_id VARCHAR(255) PRIMARY KEY,
    temporal_run_id VARCHAR(255),
    namespace VARCHAR(64) NOT NULL,
    flow_id VARCHAR(128) NOT NULL,
    flow_name VARCHAR(255),
    version VARCHAR(64),
    business_key VARCHAR(512),
    run_source VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    temporal_status VARCHAR(64),
    input_json TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    last_synced_at TIMESTAMP,
    failure_message TEXT,
    failure_type VARCHAR(64)
);

CREATE INDEX idx_flow_run_namespace_started ON flow_run (namespace, started_at DESC);
CREATE INDEX idx_flow_run_flow_id ON flow_run (flow_id);
CREATE INDEX idx_flow_run_status ON flow_run (namespace, status);

CREATE TABLE flow_node_run (
    workflow_id VARCHAR(255) NOT NULL REFERENCES flow_run (workflow_id) ON DELETE CASCADE,
    node_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(255),
    node_kind VARCHAR(32),
    activity_type VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    last_detail_json TEXT,
    PRIMARY KEY (workflow_id, node_id)
);

CREATE INDEX idx_flow_node_run_workflow ON flow_node_run (workflow_id);

CREATE TABLE flow_event (
    id BIGSERIAL PRIMARY KEY,
    workflow_id VARCHAR(255) NOT NULL REFERENCES flow_run (workflow_id) ON DELETE CASCADE,
    sequence_no INTEGER NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    node_id VARCHAR(128),
    node_name VARCHAR(255),
    node_kind VARCHAR(32),
    activity_type VARCHAR(128),
    status VARCHAR(32),
    detail_json TEXT,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_flow_event_workflow_seq UNIQUE (workflow_id, sequence_no)
);

CREATE INDEX idx_flow_event_workflow_occurred ON flow_event (workflow_id, occurred_at);
