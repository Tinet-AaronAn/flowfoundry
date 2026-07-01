package com.example.platform.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  @Test
  void evaluatesComparisonsAgainstVariables() {
    VariableStore variables =
        new VariableStore(Map.of("remainingContacts", 12, "maxRounds", 3));
    variables.assign("roundNumber", 2);

    assertThat(evaluator.evaluate("remainingContacts > 0", variables)).isTrue();
    assertThat(evaluator.evaluate("$.vars.roundNumber < maxRounds", variables)).isTrue();
    assertThat(evaluator.evaluate("remainingContacts <= 0", variables)).isFalse();
  }

  @Test
  void evaluatesBooleanLogic() {
    VariableStore variables =
        new VariableStore(Map.of("complaintRate", 0.01, "failedRate", 0.5, "vipSegment", true));

    assertThat(evaluator.evaluate("complaintRate > 0.03 or failedRate > 0.4", variables)).isTrue();
    assertThat(evaluator.evaluate("vipSegment == true and failedRate < 0.8", variables)).isTrue();
    assertThat(evaluator.evaluate("not vipSegment", variables)).isFalse();
  }
}
