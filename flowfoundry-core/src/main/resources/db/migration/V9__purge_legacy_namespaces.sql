-- Retain ai-collection-strategy only; purge legacy namespace data from platform tables.

DELETE FROM workflow_definition
WHERE namespace <> 'ai-collection-strategy';

DELETE FROM platform_id_registry
WHERE kind = 'workflow'
  AND id NOT IN (SELECT id FROM workflow_definition);

DELETE FROM platform_audit_log
WHERE namespace IS NOT NULL
  AND namespace <> 'ai-collection-strategy';

DELETE FROM platform_api_key_namespace
WHERE namespace <> 'ai-collection-strategy';

DELETE FROM platform_api_key
WHERE id IN ('vcs', 'system2', 'platform-modeler');

DELETE FROM platform_api_key k
WHERE k.id <> 'platform-admin'
  AND k.admin_flag = false
  AND NOT EXISTS (
      SELECT 1 FROM platform_api_key_namespace n WHERE n.api_key_id = k.id
  );

DELETE FROM platform_namespace
WHERE id <> 'ai-collection-strategy';
