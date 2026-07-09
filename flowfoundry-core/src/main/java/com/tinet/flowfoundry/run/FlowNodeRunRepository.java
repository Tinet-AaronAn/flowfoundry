package com.tinet.flowfoundry.run;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowNodeRunRepository extends JpaRepository<FlowNodeRunEntity, FlowNodeRunEntity.FlowNodeRunId> {

  List<FlowNodeRunEntity> findByWorkflowIdOrderByStartedAtAsc(String workflowId);
}
