package com.tinet.flowfoundary.interpreter.runtime;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.Map;

public class VariableStore {

  private final Map<String, Object> input;
  private final Map<String, Object> vars = new LinkedHashMap<>();
  private Object lastResult;

  public VariableStore(Map<String, Object> input) {
    this.input = input == null ? Map.of() : new LinkedHashMap<>(input);
  }

  public Map<String, Object> input() {
    return input;
  }

  public Map<String, Object> variables() {
    return new LinkedHashMap<>(vars);
  }

  public Object lastResult() {
    return lastResult;
  }

  public void setLastResult(Object lastResult) {
    this.lastResult = lastResult;
  }

  public Object resolve(String expression) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    String path = normalizePath(expression);
    if ("$".equals(path)) {
      return snapshot();
    }
    if (path.startsWith("$.input.")) {
      return readPath(input, path.substring("$.input.".length()));
    }
    if (path.startsWith("$.vars.")) {
      return readPath(vars, path.substring("$.vars.".length()));
    }
    if (path.startsWith("$.lastResult")) {
      if ("$.lastResult".equals(path)) {
        return lastResult;
      }
      return readPath(lastResult, path.substring("$.lastResult.".length()));
    }
    if (vars.containsKey(path)) {
      return vars.get(path);
    }
    if (input.containsKey(path)) {
      return input.get(path);
    }
    return readPath(lastResult, path);
  }

  public void assign(String target, Object value) {
    if (target == null || target.isBlank()) {
      return;
    }
    String path = normalizePath(target);
    if (path.startsWith("$.vars.")) {
      path = path.substring("$.vars.".length());
    }
    if (path.startsWith("$.input.") || path.startsWith("$.lastResult")) {
      throw new IllegalArgumentException("Only workflow variables can be assigned: " + target);
    }
    writePath(vars, path, value);
  }

  public Map<String, Object> snapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("input", input());
    snapshot.put("vars", variables());
    snapshot.put("lastResult", lastResult);
    return snapshot;
  }

  private static String normalizePath(String expression) {
    String value = expression.trim();
    if (value.startsWith("${") && value.endsWith("}")) {
      value = value.substring(2, value.length() - 1).trim();
    }
    return value;
  }

  @SuppressWarnings("unchecked")
  private static Object readPath(Object root, String path) {
    if (root == null || path == null || path.isBlank()) {
      return root;
    }
    Object current = root;
    for (String segment : path.split("\\.")) {
      if (current == null) {
        return null;
      }
      if (current instanceof Map<?, ?> map) {
        current = map.get(segment);
      } else if (current instanceof Record) {
        current = readRecordComponent(current, segment);
      } else {
        current = readBeanAccessor(current, segment);
      }
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private static void writePath(Map<String, Object> root, String path, Object value) {
    String[] segments = path.split("\\.");
    Map<String, Object> current = root;
    for (int i = 0; i < segments.length - 1; i++) {
      Object child = current.get(segments[i]);
      if (!(child instanceof Map<?, ?>)) {
        child = new LinkedHashMap<String, Object>();
        current.put(segments[i], child);
      }
      current = (Map<String, Object>) child;
    }
    current.put(segments[segments.length - 1], value);
  }

  private static Object readRecordComponent(Object record, String name) {
    try {
      for (RecordComponent component : record.getClass().getRecordComponents()) {
        if (component.getName().equals(name)) {
          return component.getAccessor().invoke(record);
        }
      }
      return null;
    } catch (ReflectiveOperationException e) {
      throw new IllegalArgumentException("Cannot read record field " + name, e);
    }
  }

  private static Object readBeanAccessor(Object bean, String name) {
    String suffix = Character.toUpperCase(name.charAt(0)) + name.substring(1);
    for (String methodName : new String[] {name, "get" + suffix, "is" + suffix}) {
      try {
        return bean.getClass().getMethod(methodName).invoke(bean);
      } catch (ReflectiveOperationException ignored) {
        // Try the next conventional accessor.
      }
    }
    return null;
  }
}
