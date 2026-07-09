package com.tinet.flowfoundry.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowRunStatusServiceTest {

  @Test
  void buildsTemporalHistoryUrlWithRunId() {
    String url =
        FlowRunStatusService.buildTemporalHistoryUrl(
            "ai-collection-strategy",
            "http://127.0.0.1:8080",
            "workflow_test_abc",
            "0192abcd-efgh-ijkl-mnop-qrstuvwxyz12");
    assertThat(url)
        .isEqualTo(
            "http://127.0.0.1:8080/namespaces/ai-collection-strategy/workflows/workflow_test_abc/0192abcd-efgh-ijkl-mnop-qrstuvwxyz12/history");
  }

  @Test
  void buildsTemporalHistoryUrlWithoutRunId() {
    String url =
        FlowRunStatusService.buildTemporalHistoryUrl(
            "default", "http://127.0.0.1:8080/", "workflow_demo", null);
    assertThat(url)
        .isEqualTo("http://127.0.0.1:8080/namespaces/default/workflows/workflow_demo/history");
  }

  @Test
  void extractsParentNodeIdFromChildBusinessKey() {
    assertThat(
            FlowRunStatusService.parentNodeIdFromBusinessKey(
                "default:workflow_parent-uuid/task_fdommvh8"))
        .isEqualTo("task_fdommvh8");
    assertThat(FlowRunStatusService.parentNodeIdFromBusinessKey("default:workflow_parent-uuid"))
        .isNull();
    assertThat(FlowRunStatusService.parentNodeIdFromBusinessKey(null)).isNull();
  }
}
