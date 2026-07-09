package com.tinet.flowfoundry.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundry.activity.ActivityTypes;
import com.tinet.flowfoundry.flow.FlowCompiler;
import com.tinet.flowfoundry.flow.FlowDefinition;
import com.tinet.flowfoundry.flow.FlowEdge;
import com.tinet.flowfoundry.flow.FlowMetadata;
import com.tinet.flowfoundry.flow.FlowNode;
import com.tinet.flowfoundry.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundry.interpreter.runtime.VariableStore;
import com.tinet.flowfoundry.registry.ActivityCatalogService;
import com.tinet.flowfoundry.registry.ActivityRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FlowInterpreterEngineHumanTaskTest {

  private final FlowCompiler compiler =
      new FlowCompiler(
          ActivityCatalogService.forRegistries(
              null, new ActivityRegistry("1.0", "test", "test-task-queue", List.of())));

  @Test
  void humanTaskInvokesActivityAndWaitsForCompletion() throws Exception {
    ExecutionPlan plan = compileHumanFlow();
    VariableStore variables = new VariableStore(Map.of());
    SyncEnginePort port = SyncEnginePort.withActivityResult(ActivityTypes.HUMAN_TASK, Map.of("waiting", true));
    port.setAutoCompleteManagedHumanTasks(true);

    new FlowInterpreterEngine().runUntilEnd(plan, variables, port);

    assertThat(port.activityCalls()).isEqualTo(1);
    assertThat(variables.resolve("$.vars.humanTask.Review.outcome")).isEqualTo("approved");
  }

  private ExecutionPlan compileHumanFlow() {
    FlowDefinition definition =
        new FlowDefinition(
            "1.0",
            new FlowMetadata("HumanFlow", "Human Flow", "1.0.0"),
            Map.of(),
            Map.of(),
            List.of(
                node("Start", "START"),
                humanNode("Review"),
                node("End", "END")),
            List.of(
                new FlowEdge("Start", "Review", "default"),
                new FlowEdge("Review", "End", "default")));
    return compiler.compile(definition);
  }

  private static FlowNode node(String id, String kind) {
    return new FlowNode(
        id, kind, id, null, null, null, null, null, null, null, null, null, null, null, null, Map.of());
  }

  private static FlowNode humanNode(String id) {
    return new FlowNode(
        id,
        "HUMAN_TASK",
        id,
        "humanTask",
        ActivityTypes.HUMAN_TASK,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        Map.of(
            "flowFoundryHumanTask", Map.of("mode", "managed"),
            "nodeId", id));
  }
}
