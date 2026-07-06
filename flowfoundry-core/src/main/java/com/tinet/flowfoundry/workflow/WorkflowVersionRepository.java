package com.tinet.flowfoundry.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersionEntity, WorkflowVersionId> {

  List<WorkflowVersionEntity> findByWorkflowIdOrderByCreatedAtAsc(String workflowId);
}
