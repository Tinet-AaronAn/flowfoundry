package com.tinet.flowfoundary.api;

import com.tinet.flowfoundary.workflow.WorkflowConflictException;
import com.tinet.flowfoundary.workflow.WorkflowNotFoundException;
import com.tinet.flowfoundary.workflow.WorkflowRunId;
import com.tinet.flowfoundary.workflow.WorkflowVersionNotFoundException;
import io.temporal.api.common.v1.WorkflowExecution;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WorkflowApiExceptionHandler {

  @ExceptionHandler(WorkflowNotFoundException.class)
  public ResponseEntity<Map<String, String>> notFound(WorkflowNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(io.temporal.client.WorkflowNotFoundException.class)
  public ResponseEntity<Map<String, String>> temporalRunNotFound(
      io.temporal.client.WorkflowNotFoundException ex) {
    String workflowId = executionWorkflowId(ex.getExecution());
    String message = "Temporal workflow run not found: " + workflowId;
    if (WorkflowRunId.isDefinitionId(workflowId)) {
      message +=
          ". This is a workflow definition ID (workflow_{8-char}), not a Temporal run ID."
              + " Use the execution ID from Run Instances (format: workflow_{flowId}_{uuid}).";
    }
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", message));
  }

  private static String executionWorkflowId(WorkflowExecution execution) {
    if (execution == null || execution.getWorkflowId().isBlank()) {
      return "unknown";
    }
    return execution.getWorkflowId();
  }

  @ExceptionHandler(WorkflowVersionNotFoundException.class)
  public ResponseEntity<Map<String, String>> versionNotFound(WorkflowVersionNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(WorkflowConflictException.class)
  public ResponseEntity<Map<String, String>> conflict(WorkflowConflictException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("message", ex.getMessage()));
  }
}
