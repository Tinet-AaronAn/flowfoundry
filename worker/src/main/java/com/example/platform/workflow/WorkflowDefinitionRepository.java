package com.example.platform.workflow;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, String> {

  @Query(
      """
      SELECT w FROM WorkflowDefinitionEntity w
      WHERE (:keyword IS NULL OR :keyword = ''
        OR LOWER(w.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
        OR LOWER(w.id) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:status IS NULL OR :status = '' OR w.status = :status)
      ORDER BY w.updatedAt DESC
      """)
  List<WorkflowDefinitionEntity> search(
      @Param("keyword") String keyword, @Param("status") String status);
}
