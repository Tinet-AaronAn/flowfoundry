-- 系统 namespace 显示名与 ID 保持一致。
UPDATE platform_namespace
SET display_name = 'flowfoundry-system',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'flowfoundry-system'
  AND display_name <> 'flowfoundry-system';
