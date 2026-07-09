-- Unified namespace model: align legacy namespaces to Activity Registry namespace.

INSERT INTO platform_namespace (id, display_name, description, created_at, updated_at)
SELECT
  'ai-collection-strategy',
  'ai-collection-strategy',
  'App namespace (workflows, Temporal, Activity Registry)',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (
  SELECT 1 FROM platform_namespace WHERE id = 'ai-collection-strategy'
);

UPDATE platform_namespace
SET display_name = 'ai-collection-strategy',
    description = 'App namespace (workflows, Temporal, Activity Registry)',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'ai-collection-strategy';

UPDATE workflow_definition
SET namespace = 'ai-collection-strategy'
WHERE namespace IN ('default', 'flowfoundry-system', 'call-campaign');

UPDATE platform_audit_log
SET namespace = 'ai-collection-strategy'
WHERE namespace IN ('default', 'flowfoundry-system', 'call-campaign');

UPDATE platform_api_key_namespace
SET namespace = 'ai-collection-strategy'
WHERE namespace IN ('default', 'flowfoundry-system', 'call-campaign');

DELETE FROM platform_namespace
WHERE id IN ('default', 'flowfoundry-system', 'call-campaign');
