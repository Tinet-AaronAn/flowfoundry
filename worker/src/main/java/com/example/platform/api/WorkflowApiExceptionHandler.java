package com.example.platform.api;

import com.example.platform.workflow.WorkflowConflictException;
import com.example.platform.workflow.WorkflowNotFoundException;
import com.example.platform.workflow.WorkflowVersionNotFoundException;
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
