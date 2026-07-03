package com.tinet.flowfoundary.workflow;

import com.tinet.flowfoundary.interpreter.runtime.RunSource;
import java.util.UUID;
import java.util.regex.Pattern;

public final class WorkflowRunId {

  private static final Pattern DEFINITION_ID = Pattern.compile("^workflow_[a-z0-9]{8}$");

  private WorkflowRunId() {}

  public static String forRun(RunSource runSource, String flowId) {
    if (runSource != null && runSource.usesStubActivities()) {
      return forWebModelerRun(flowId);
    }
    return forProductionRun(flowId);
  }

  public static String forWebModelerRun(String flowId) {
    return "workflow_test_" + sanitize(flowId) + "_" + UUID.randomUUID();
  }

  public static String forProductionRun(String flowId) {
    return "workflow_" + sanitize(flowId) + "_" + UUID.randomUUID();
  }

  /** @deprecated use {@link #forProductionRun} or {@link #forRun} */
  @Deprecated
  public static String forFlow(String flowId) {
    return forProductionRun(flowId);
  }

  public static String forChildWorkflow(
      RunSource runSource, String childFlowId, String businessKey) {
    if (runSource != null && runSource.usesStubActivities()) {
      return "workflow_test_child_"
          + sanitize(childFlowId)
          + "_"
          + sanitize(businessKey);
    }
    return forChildWorkflow(childFlowId, businessKey);
  }

  public static String forChildWorkflow(String childFlowId, String businessKey) {
    return "workflow_child_"
        + sanitize(childFlowId)
        + "_"
        + sanitize(businessKey);
  }

  public static void requireTemporalRunId(String workflowId) {
    if (workflowId == null || workflowId.isBlank()) {
      throw new IllegalArgumentException("Workflow ID is required");
    }
    if (!workflowId.startsWith("workflow_")) {
      throw new IllegalArgumentException("Workflow ID must start with workflow_");
    }
  }

  public static boolean isDefinitionId(String workflowId) {
    return workflowId != null && DEFINITION_ID.matcher(workflowId).matches();
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.replaceAll("[^a-zA-Z0-9_-]", "_");
  }
}
