-- 去除 "client" 概念：API Key 相关表/列统一改名为 api_key。
ALTER TABLE platform_api_client RENAME TO platform_api_key;
ALTER TABLE platform_api_client_namespace RENAME TO platform_api_key_namespace;
ALTER TABLE platform_api_key_namespace RENAME COLUMN client_id TO api_key_id;

ALTER INDEX idx_platform_api_client_key_hash RENAME TO idx_platform_api_key_key_hash;
ALTER INDEX idx_platform_api_client_status RENAME TO idx_platform_api_key_status;

ALTER TABLE platform_audit_log RENAME COLUMN client_id TO api_key_id;
ALTER TABLE platform_audit_log RENAME COLUMN actor_client_id TO actor_api_key_id;
ALTER INDEX idx_platform_audit_log_client_id RENAME TO idx_platform_audit_log_api_key_id;
