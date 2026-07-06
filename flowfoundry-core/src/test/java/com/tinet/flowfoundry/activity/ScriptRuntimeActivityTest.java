package com.tinet.flowfoundry.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.runtime.ActivityExecutionContext;
import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScriptRuntimeActivityTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void returnsStubResultForWebModelerRuns() {
    ScriptRuntimeActivity activity =
        new ScriptRuntimeActivity(
            objectMapper,
            new ScriptRuntimeProperties("http://127.0.0.1:9999", 30, "7000001", "flowfoundry"));

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of("scriptCodeId", "demo-script", "scriptVersion", "3"),
                "remainingContacts",
                42,
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));

    assertThat(result)
        .containsEntry("scriptCodeId", "demo-script")
        .containsEntry("scriptVersion", "3")
        .containsEntry("matched", true)
        .containsEntry("remainingContacts", 42)
        .containsEntry("nextAction", "continue");
  }

  @Test
  void failsInProductionWhenServiceUrlIsNotConfigured() {
    ScriptRuntimeActivity activity =
        new ScriptRuntimeActivity(objectMapper, new ScriptRuntimeProperties("", 30, "7000001", null));

    assertThatThrownBy(
            () ->
                activity.execute(
                    Map.of(
                        "_config",
                        Map.of("scriptCodeId", "877", "scriptVersion", "3"),
                        ActivityExecutionContext.CONTEXT_KEY,
                        productionContext())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("service URL is not configured");
  }

  @Test
  void callsIvrNodejsWithTinetFields() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
    server.createContext(
        "/ivr/nodejs/run",
        exchange -> {
          String requestBody =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          assertThat(requestBody)
              .contains("\"tinetJsCodeId\":\"877\"")
              .contains("\"tinetJsCodeVersion\":\"3\"")
              .contains("\"tinetEnterpriseId\":\"7000001\"")
              .contains("\"tinetReqUniqueId\":\"wf-runtime-1\"")
              .contains("\"remainingContacts\":153")
              .doesNotContain("_config")
              .doesNotContain("_executionContext");
          byte[] response =
              """
              {"nextAction":"review","needReview":true}
              """
                  .trim()
                  .getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          try (OutputStream out = exchange.getResponseBody()) {
            out.write(response);
          }
        });
    server.start();
    try {
      int port = server.getAddress().getPort();
      ScriptRuntimeActivity activity =
          new ScriptRuntimeActivity(
              objectMapper,
              new ScriptRuntimeProperties("http://127.0.0.1:" + port, 5, "7000001", "flowfoundry"));

      Map<String, Object> result =
          activity.execute(
              Map.of(
                  "_config",
                  Map.of("scriptCodeId", "877", "scriptVersion", "3", "scriptName", "risk-check"),
                  "remainingContacts",
                  153,
                  ActivityExecutionContext.CONTEXT_KEY,
                  productionContext(),
                  FlowFoundryTrace.INPUT_KEY,
                  Map.of("nodeId", "Script")));

      assertThat(result)
          .containsEntry("scriptCodeId", "877")
          .containsEntry("scriptVersion", "3")
          .containsEntry("scriptName", "risk-check")
          .containsEntry("nextAction", "review")
          .containsEntry("needReview", true)
          .containsEntry("matched", true);
    } finally {
      server.stop(0);
    }
  }

  @Test
  void buildIvrNodejsRequestUsesTinetFields() {
    Map<String, Object> request =
        ScriptRuntimeActivity.buildIvrNodejsRequest(
            "877",
            "3",
            "7000001",
            "req-1",
            "risk-check",
            "flowfoundry",
            3000,
            Map.of("roundNumber", 2));

    assertThat(request)
        .containsEntry("tinetJsCodeId", "877")
        .containsEntry("tinetJsCodeVersion", "3")
        .containsEntry("tinetEnterpriseId", "7000001")
        .containsEntry("tinetReqUniqueId", "req-1")
        .containsEntry("tinetScriptName", "risk-check")
        .containsEntry("tinetSourceType", "flowfoundry")
        .containsEntry("roundNumber", 2);
  }

  @Test
  void extractScriptInputsRemovesReservedKeys() {
    Map<String, Object> inputs =
        ScriptRuntimeActivity.extractScriptInputs(
            Map.of(
                "campaignId",
                "cmp-1",
                "_config",
                Map.of("scriptCodeId", "877"),
                ActivityExecutionContext.CONTEXT_KEY,
                Map.of("runSource", "production"),
                "_args",
                new Object[] {1},
                FlowFoundryTrace.INPUT_KEY,
                Map.of("nodeId", "Script")));

    assertThat(inputs).containsExactly(Map.entry("campaignId", "cmp-1"));
  }

  @Test
  void resolvesLegacyDecisionRefFromConfig() {
    ScriptRuntimeActivity activity =
        new ScriptRuntimeActivity(
            objectMapper,
            new ScriptRuntimeProperties("", 30, "7000001", null));

    Map<String, Object> result =
        activity.execute(
            Map.of(
                "_config",
                Map.of("decisionRef", "legacy-script", "decisionVersion", "2"),
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));

    assertThat(result).containsEntry("scriptCodeId", "legacy-script").containsEntry("scriptVersion", "2");
  }

  private static Map<String, Object> webContext() {
    return Map.of("runSource", "web-modeler", "businessKey", "bk-1", "workflowId", "wf-1");
  }

  private static Map<String, Object> productionContext() {
    return Map.of("runSource", "production", "businessKey", "bk-1", "workflowId", "wf-runtime-1");
  }
}
