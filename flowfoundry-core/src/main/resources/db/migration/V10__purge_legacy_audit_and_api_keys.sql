-- Follow-up purge for environments that already applied an earlier V9 revision.

DELETE FROM platform_api_key_namespace
WHERE namespace <> 'ai-collection-strategy';

DELETE FROM platform_api_key
WHERE id IN (
  'cc',
  'vcs',
  'system2',
  'platform-modeler',
  'my-app',
  'app-mra03ecj',
  'addd1',
  'test-key-debug'
);

DELETE FROM platform_api_key k
WHERE k.id <> 'platform-admin'
  AND k.admin_flag = false
  AND NOT EXISTS (
      SELECT 1 FROM platform_api_key_namespace n WHERE n.api_key_id = k.id
  );

DELETE FROM platform_audit_log
WHERE namespace IS NULL
   OR namespace <> 'ai-collection-strategy';

DELETE FROM platform_audit_log
WHERE resource_id IN (
  'flowfoundry-system',
  'call-campaign',
  'cc-call',
  'cc-paas',
  'default',
  'vcs',
  'cc',
  'system2',
  'platform-modeler',
  'my-app',
  'app-mra03ecj',
  'addd1',
  'test-key-debug'
);

DELETE FROM platform_namespace
WHERE id <> 'ai-collection-strategy';
