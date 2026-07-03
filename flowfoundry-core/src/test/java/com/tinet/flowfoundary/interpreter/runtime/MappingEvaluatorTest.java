package com.tinet.flowfoundary.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MappingEvaluatorTest {

  private final MappingEvaluator evaluator = new MappingEvaluator();

  @Test
  void passesThroughWorkflowInputWhenInputMappingIsEmpty() {
    VariableStore variables =
        new VariableStore(
            Map.of(
                "campaignId", "demo-campaign",
                "roundNumber", 1,
                "maxRounds", 3));

    Map<String, Object> input = evaluator.buildInput(variables, Map.of());

    assertThat(input)
        .containsEntry("campaignId", "demo-campaign")
        .containsEntry("roundNumber", 1)
        .containsEntry("maxRounds", 3);
  }

  @Test
  void usesExplicitInputMappingWhenProvided() {
    VariableStore variables = new VariableStore(Map.of("campaignId", "demo-campaign", "roundNumber", 1));

    Map<String, Object> input =
        evaluator.buildInput(
            variables,
            Map.of("campaignId", "$.input.campaignId"),
            InputMappingMode.PASSTHROUGH_UNMAPPED);

    assertThat(input)
        .containsEntry("campaignId", "demo-campaign")
        .containsEntry("roundNumber", 1);
  }

  @Test
  void usesMappedOnlyModeWhenConfigured() {
    VariableStore variables =
        new VariableStore(
            Map.of(
                "campaignId", "demo-campaign",
                "roundNumber", 1));

    Map<String, Object> input =
        evaluator.buildInput(
            variables,
            Map.of("campaignId", "$.input.campaignId"),
            InputMappingMode.MAPPED_ONLY);

    assertThat(input).containsExactly(Map.entry("campaignId", "demo-campaign"));
  }

  @Test
  void passesThroughUnmappedFieldsWhenModeAllows() {
    VariableStore variables =
        new VariableStore(
            Map.of(
                "campaignId", "demo-campaign",
                "roundNumber", 1));

    Map<String, Object> input =
        evaluator.buildInput(
            variables,
            Map.of("campaignId", "$.input.campaignId"),
            InputMappingMode.PASSTHROUGH_UNMAPPED);

    assertThat(input)
        .containsEntry("campaignId", "demo-campaign")
        .containsEntry("roundNumber", 1);
  }

  @Test
  void buildsPositionalArgumentsFromVariablePaths() {
    VariableStore variables = new VariableStore(Map.of("campaignId", "cmp-1"));
    variables.assign("roundNumber", 2);

    Object[] args =
        evaluator.buildArguments(
            variables, List.of("$.input.campaignId", "$.vars.roundNumber"));

    assertThat(args).containsExactly("cmp-1", 2);
  }

  @Test
  void mapsRecordResultToVariables() {
    VariableStore variables = new VariableStore(Map.of());

    evaluator.applyOutput(
        variables,
        new AggregateResult(10, 2),
        Map.of(
            "remainingContacts", "$.lastResult.remainingContacts",
            "roundNumber", "$.lastResult.nextRoundNumber"));

    assertThat(variables.variables())
        .containsEntry("remainingContacts", 10)
        .containsEntry("roundNumber", 2);
  }

  private record AggregateResult(int remainingContacts, int nextRoundNumber) {}
}
