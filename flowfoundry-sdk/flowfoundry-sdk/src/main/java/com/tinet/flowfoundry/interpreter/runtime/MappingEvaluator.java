package com.tinet.flowfoundry.interpreter.runtime;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MappingEvaluator {

  public Map<String, Object> buildInput(
      VariableStore variables, Map<String, String> inputMapping) {
    return buildInput(variables, inputMapping, InputMappingMode.PASSTHROUGH_UNMAPPED);
  }

  public Map<String, Object> buildInput(
      VariableStore variables, Map<String, String> inputMapping, InputMappingMode mode) {
    if (inputMapping == null || inputMapping.isEmpty()) {
      return new LinkedHashMap<>(variables.input());
    }
    InputMappingMode effectiveMode =
        mode == null ? InputMappingMode.PASSTHROUGH_UNMAPPED : mode;
    Map<String, Object> input = new LinkedHashMap<>();
    inputMapping.forEach((target, source) -> input.put(target, variables.resolve(source)));
    if (effectiveMode == InputMappingMode.PASSTHROUGH_UNMAPPED) {
      variables.input().forEach(input::putIfAbsent);
    }
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
