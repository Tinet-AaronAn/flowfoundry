package com.example.platform.flow;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.interpreter.runtime.ConditionEvaluator;
import com.example.platform.interpreter.runtime.VariableStore;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SafeFeelCompilerTest {

  private final SafeFeelCompiler compiler = new SafeFeelCompiler();
  private final ConditionEvaluator evaluator = new ConditionEvaluator();

  @Test
  void preservesDoubleEqualsOperatorBeforeSingleEquals() {
    Object condition = compiler.compile("${vipSegment == true}");

    assertThat(evaluator.evaluate(condition, new VariableStore(Map.of("vipSegment", false)))).isFalse();
    assertThat(evaluator.evaluate(condition, new VariableStore(Map.of("vipSegment", true)))).isTrue();
  }
}
