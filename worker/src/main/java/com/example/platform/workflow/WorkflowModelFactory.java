package com.example.platform.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WorkflowModelFactory {

  private final ObjectMapper objectMapper;

  public WorkflowModelFactory(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public JsonNode emptyModel(String workflowId, String name) {
    ObjectNode model = objectMapper.createObjectNode();
    model.put("id", workflowId);
    model.put("name", name);
    model.put("targetNamespace", "https://example.com/bpmn/flowfoundry");
    ObjectNode process = model.putObject("process");
    process.put("id", workflowId.replaceFirst("^workflow_", "process_"));
    process.put("name", name);
    process.put("isExecutable", true);
    process.put("edgeRouting", "orthogonal");
    model.putArray("nodes");
    model.putArray("edges");
    return model;
  }
}
