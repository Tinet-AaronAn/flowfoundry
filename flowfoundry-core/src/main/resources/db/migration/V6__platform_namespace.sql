CREATE TABLE platform_namespace (
    id VARCHAR(64) PRIMARY KEY,
    display_name VARCHAR(255) NOT NULL,
    description VARCHAR(1024),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_platform_namespace_updated_at ON platform_namespace (updated_at DESC);

-- 系统 namespace 与 Temporal 系统 namespace 同名。
INSERT INTO platform_namespace (id, display_name, description, created_at, updated_at)
VALUES (
    'flowfoundry-system',
    'FlowFoundry System',
    'Platform management and modeler debug runs',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
);

-- 将 workflow 定义里已出现、但尚未登记的 namespace 补录进注册表。
INSERT INTO platform_namespace (id, display_name, description, created_at, updated_at)
SELECT DISTINCT w.namespace,
       w.namespace,
       NULL,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM workflow_definition w
WHERE w.namespace IS NOT NULL
  AND w.namespace <> ''
  AND w.namespace <> 'flowfoundry-system'
  AND NOT EXISTS (
      SELECT 1 FROM platform_namespace p WHERE p.id = w.namespace
  );
