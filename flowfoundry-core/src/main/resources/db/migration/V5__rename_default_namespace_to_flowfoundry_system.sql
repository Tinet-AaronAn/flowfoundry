-- 平台逻辑 namespace 与 Temporal 系统 namespace 统一为 flowfoundry-system。
UPDATE workflow_definition
SET namespace = 'flowfoundry-system'
WHERE namespace = 'default';

UPDATE platform_audit_log
SET namespace = 'flowfoundry-system'
WHERE namespace = 'default';
