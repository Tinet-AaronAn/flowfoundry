ALTER TABLE workflow_definition
    ADD COLUMN namespace VARCHAR(64) NOT NULL DEFAULT 'default';

CREATE INDEX idx_workflow_definition_namespace ON workflow_definition (namespace);
