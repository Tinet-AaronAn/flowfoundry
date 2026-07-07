package com.tinet.flowfoundry.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface PlatformAuditLogRepository
    extends JpaRepository<PlatformAuditLogEntity, Long>,
        JpaSpecificationExecutor<PlatformAuditLogEntity> {}
