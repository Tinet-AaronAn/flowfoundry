package com.tinet.flowfoundry.run;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface FlowRunRepository
    extends JpaRepository<FlowRunEntity, String>, JpaSpecificationExecutor<FlowRunEntity> {}
