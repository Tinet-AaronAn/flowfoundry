package com.example.platform.interpreter.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MappingEvaluator {

  public Map<String, Object> buildInput(
      VariableStore variables, Map<String, String> inputMapping) {
    Map<String, Object> input = new LinkedHashMap<>();
    if (inputMapping == null) {
      return input;
    }
    inputMapping.forEach((target, source) -> input.put(target, variables.resolve(source)));
    return input;
  }

  public Object[] buildArguments(VariableStore variables, List<String> inputArgs) {
    if (inputArgs == null || inputArgs.isEmpty()) {
      return new Object[0];
    }
    return inputArgs.stream().map(variables::resolve).toArray(Object[]::new);
  }

  public void applyOutput(
      VariableStore variables, Object result, Map<String, String> outputMapping) {
    variables.setLastResult(result);
    if (outputMapping == null || outputMapping.isEmpty()) {
      return;
    }
    outputMapping.forEach(
        (target, source) -> variables.assign(target, variables.resolve(source)));
  }
}
