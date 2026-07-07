package com.tinet.flowfoundry.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlowRunStatusServiceTest {

  @Test
  void buildsTemporalHistoryUrlWithRunId() {
    String url =
        FlowRunStatusService.buildTemporalHistoryUrl(
            "call-campaign",
            "http://127.0.0.1:8080",
            "workflow_test_abc",
            "0192abcd-efgh-ijkl-mnop-qrstuvwxyz12");
    assertThat(url)
        .isEqualTo(
            "http://127.0.0.1:8080/namespaces/call-campaign/workflows/workflow_test_abc/0192abcd-efgh-ijkl-mnop-qrstuvwxyz12/history");
  }

  @Test
  void buildsTemporalHistoryUrlWithoutRunId() {
    String url =
        FlowRunStatusService.buildTemporalHistoryUrl(
            "default", "http://127.0.0.1:8080/", "workflow_demo", null);
    assertThat(url)
        .isEqualTo("http://127.0.0.1:8080/namespaces/default/workflows/workflow_demo/history");
  }
}
