package com.tinet.flowfoundry.run;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FlowEventRepository extends JpaRepository<FlowEventEntity, Long> {

  List<FlowEventEntity> findByWorkflowIdOrderBySequenceNoAsc(String workflowId);

  boolean existsByWorkflowIdAndSequenceNo(String workflowId, int sequenceNo);
}
