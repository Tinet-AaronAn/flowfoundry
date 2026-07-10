package com.tinet.flowfoundry.client;

import com.tinet.flowfoundry.contract.FlowApiContracts.RunRequest;
import com.tinet.flowfoundry.contract.FlowApiContracts.RunResponse;
import com.tinet.flowfoundry.contract.FlowApiContracts.StartSavedWorkflowRequest;
import com.tinet.flowfoundry.contract.RunStatusResponse;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import com.tinet.flowfoundry.run.FlowRunContracts.FlowRunListPage;
import com.tinet.flowfoundry.run.FlowRunEventCommand;
import com.tinet.flowfoundry.workflow.NamespaceContextDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.AllocateIdResponse;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.CreateWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.SaveWorkflowVersionRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.UpdateWorkflowRequest;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowRecordDto;
import com.tinet.flowfoundry.workflow.WorkflowContracts.WorkflowVersionDto;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

class DefaultFlowFoundryPlatformClient implements FlowFoundryPlatformClient {

  private final RestClient restClient;
  private final FlowFoundryPlatformProperties properties;

  DefaultFlowFoundryPlatformClient(RestClient restClient, FlowFoundryPlatformProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  @Override
  public Map<String, Object> getPublicConfig() {
    return restClient
        .get()
        .uri("/api/platform/public-config")
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public NamespaceContextDto getWorkflowContext() {
    return restClient.get().uri("/api/workflows/context").retrieve().body(NamespaceContextDto.class);
  }

  @Override
  public List<WorkflowRecordDto> listWorkflows(String keyword, String status) {
  UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/workflows");
    if (keyword != null && !keyword.isBlank()) {
      builder.queryParam("keyword", keyword);
    }
    if (status != null && !status.isBlank()) {
      builder.queryParam("status", status);
    }
    return restClient
        .get()
        .uri(builder.build().toUriString())
        .retrieve()
        .body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public WorkflowRecordDto getWorkflow(String workflowId) {
    return restClient
        .get()
        .uri("/api/workflows/{workflowId}", workflowId)
        .retrieve()
        .body(WorkflowRecordDto.class);
  }

  @Override
  public WorkflowVersionDto getWorkflowVersion(String workflowId, String version) {
    return restClient
        .get()
        .uri("/api/workflows/{workflowId}/versions/{version}", workflowId, version)
        .retrieve()
        .body(WorkflowVersionDto.class);
  }

  @Override
  public WorkflowRecordDto createWorkflow(CreateWorkflowRequest request) {
    return restClient
        .post()
        .uri("/api/workflows")
        .body(request)
        .retrieve()
        .body(WorkflowRecordDto.class);
  }

  @Override
  public WorkflowRecordDto saveWorkflowVersion(
      String workflowId, String version, SaveWorkflowVersionRequest request) {
    return restClient
        .put()
        .uri("/api/workflows/{workflowId}/versions/{version}", workflowId, version)
        .body(request)
        .retrieve()
        .body(WorkflowRecordDto.class);
  }

  @Override
  public WorkflowRecordDto createWorkflowVersion(
      String workflowId, CreateWorkflowVersionRequest request) {
    return restClient
        .post()
        .uri("/api/workflows/{workflowId}/versions", workflowId)
        .body(request)
        .retrieve()
        .body(WorkflowRecordDto.class);
  }

  @Override
  public WorkflowRecordDto updateWorkflow(String workflowId, UpdateWorkflowRequest request) {
    return restClient
        .patch()
        .uri("/api/workflows/{workflowId}", workflowId)
        .body(request)
        .retrieve()
        .body(WorkflowRecordDto.class);
  }

  @Override
  public void deleteWorkflow(String workflowId) {
    restClient.delete().uri("/api/workflows/{workflowId}", workflowId).retrieve().toBodilessEntity();
  }

  @Override
  public AllocateIdResponse allocateId(AllocateIdRequest request) {
    return restClient
        .post()
        .uri("/api/workflows/ids")
        .body(request)
        .retrieve()
        .body(AllocateIdResponse.class);
  }

  @Override
  public Map<String, Object> idKinds() {
    return restClient.get().uri("/api/workflows/ids/kinds").retrieve().body(new ParameterizedTypeReference<>() {});
  }

  @Override
  public ActivityRegistry listActivities() {
    return restClient.get().uri("/api/activities").retrieve().body(ActivityRegistry.class);
  }

  @Override
  public ExecutionPlan compileFlow(FlowDefinition definition) {
    return restClient
        .post()
        .uri("/api/flows/compile")
        .body(definition)
        .retrieve()
        .body(ExecutionPlan.class);
  }

  @Override
  public RunResponse runFlow(RunRequest request) {
    return restClient.post().uri("/api/flows/run").body(request).retrieve().body(RunResponse.class);
  }

  @Override
  public RunResponse startWorkflowVersion(
      String workflowId, String version, StartSavedWorkflowRequest request) {
    StartSavedWorkflowRequest body =
        request == null ? new StartSavedWorkflowRequest(null, null, null) : request;
    return restClient
        .post()
        .uri("/api/workflows/{workflowId}/versions/{version}/run", workflowId, version)
        .body(body)
        .retrieve()
        .body(RunResponse.class);
  }

  @Override
  public FlowRunListPage listRuns(String keyword, int page, int size) {
    UriComponentsBuilder builder =
        UriComponentsBuilder.fromPath("/api/flows/runs")
            .queryParam("page", page)
            .queryParam("size", size);
    if (keyword != null && !keyword.isBlank()) {
      builder.queryParam("keyword", keyword);
    }
    return restClient
        .get()
        .uri(builder.build().toUriString())
        .retrieve()
        .body(FlowRunListPage.class);
  }

  @Override
  public RunStatusResponse getRunStatus(String workflowId) {
    return restClient
        .get()
        .uri("/api/flows/runs/{workflowId}", workflowId)
        .retrieve()
        .body(RunStatusResponse.class);
  }

  @Override
  public void completeHumanTask(String workflowId, HumanTaskCompletion completion) {
    restClient
        .post()
        .uri("/api/flows/runs/{workflowId}/human-task", workflowId)
        .body(completion)
        .retrieve()
        .toBodilessEntity();
  }

  @Override
  public void recordRunEvent(FlowRunEventCommand command) {
    restClient
        .post()
        .uri("/api/flows/runs/events")
        .body(command)
        .retrieve()
        .toBodilessEntity();
  }

  static RestClient buildRestClient(FlowFoundryPlatformProperties properties) {
    int connectMs = parseTimeoutMillis(properties.connectTimeout(), 5_000);
    int readMs = parseTimeoutMillis(properties.readTimeout(), 30_000);
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .defaultHeaders(
            headers -> {
              if (!properties.apiKey().isBlank()) {
                headers.set(FlowFoundryHttpHeaders.API_KEY, properties.apiKey());
              }
              if (!properties.namespace().isBlank()) {
                headers.set(FlowFoundryHttpHeaders.PLATFORM_NAMESPACE, properties.namespace());
              }
              headers.setContentType(MediaType.APPLICATION_JSON);
            })
        .requestFactory(
            new org.springframework.http.client.SimpleClientHttpRequestFactory() {
              {
                setConnectTimeout(connectMs);
                setReadTimeout(readMs);
              }
            })
        .build();
  }

  private static int parseTimeoutMillis(String raw, int defaultMillis) {
    if (raw == null || raw.isBlank()) {
      return defaultMillis;
    }
    String value = raw.trim().toLowerCase();
    try {
      if (value.endsWith("ms")) {
        return Integer.parseInt(value.substring(0, value.length() - 2));
      }
      if (value.endsWith("s")) {
        return Integer.parseInt(value.substring(0, value.length() - 1)) * 1000;
      }
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultMillis;
    }
  }
}
