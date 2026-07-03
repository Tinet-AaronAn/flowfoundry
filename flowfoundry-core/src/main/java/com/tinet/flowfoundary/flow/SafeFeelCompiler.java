package com.example.platform.flow;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SafeFeelCompiler {

  private static final Pattern COMPARISON =
      Pattern.compile("^(.+?)\\s*(==|!=|>=|<=|=|>|<)\\s*(.+)$");

  public Object compile(Object condition) {
    if (condition == null) {
      return "default";
    }
    if (condition instanceof Map<?, ?> map) {
      return map;
    }
    String expression = String.valueOf(condition).trim();
    if (expression.isBlank() || "default".equalsIgnoreCase(expression)) {
      return "default";
    }
    Map<String, Object> ast = compileExpression(unwrapExpression(expression));
    return Map.of("language", "safe-feel-ast", "display", expression, "ast", ast);
  }

  private Map<String, Object> compileExpression(String expression) {
    List<String> orTerms = splitTopLevel(expression, " or ");
    if (orTerms.size() > 1) {
      return Map.of("op", "or", "args", orTerms.stream().map(this::compileExpression).toList());
    }
    List<String> andTerms = splitTopLevel(expression, " and ");
    if (andTerms.size() > 1) {
      return Map.of("op", "and", "args", andTerms.stream().map(this::compileExpression).toList());
    }
    String atom = stripParentheses(expression.trim());
    if (atom.regionMatches(true, 0, "not ", 0, 4)) {
      return Map.of("op", "not", "arg", compileExpression(atom.substring(4)));
    }
    Matcher matcher = COMPARISON.matcher(atom);
    if (!matcher.matches()) {
      return Map.of("op", "truthy", "arg", operand(atom));
    }
    String operator = matcher.group(2);
    if ("==".equals(operator)) {
      operator = "=";
    }
    Map<String, Object> ast = new LinkedHashMap<>();
    ast.put("op", operator);
    ast.put("left", operand(matcher.group(1)));
    ast.put("right", operand(matcher.group(3)));
    return ast;
  }

  private static Object operand(String raw) {
    String value = stripParentheses(raw.trim());
    String lower = value.toLowerCase();
    if ("true".equals(lower)) {
      return true;
    }
    if ("false".equals(lower)) {
      return false;
    }
    if ("null".equals(lower)) {
      return null;
    }
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      return value.substring(1, value.length() - 1);
    }
    if (value.matches("-?\\d+(\\.\\d+)?")) {
      return new BigDecimal(value);
    }
    String normalized = normalizeVariablePath(value);
    return Map.of("var", normalized);
  }

  private static String normalizeVariablePath(String raw) {
    String value = raw.trim();
    if (value.startsWith("$.input.") || value.startsWith("$.vars.") || value.startsWith("$.lastResult")) {
      return value.substring(2);
    }
    if (value.startsWith("input.") || value.startsWith("vars.") || value.startsWith("lastResult")) {
      return value;
    }
    return value;
  }

  private static String unwrapExpression(String expression) {
    if (expression.startsWith("${") && expression.endsWith("}")) {
      return expression.substring(2, expression.length() - 1).trim();
    }
    return expression;
  }

  private static String stripParentheses(String expression) {
    String value = expression.trim();
    while (value.startsWith("(") && value.endsWith(")") && balanced(value.substring(1, value.length() - 1))) {
      value = value.substring(1, value.length() - 1).trim();
    }
    return value;
  }

  private static List<String> splitTopLevel(String expression, String delimiter) {
    java.util.ArrayList<String> parts = new java.util.ArrayList<>();
    int depth = 0;
    int start = 0;
    String lower = expression.toLowerCase();
    String needle = delimiter.toLowerCase();
    for (int i = 0; i <= expression.length() - needle.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      }
      if (depth == 0 && lower.startsWith(needle, i)) {
        parts.add(expression.substring(start, i).trim());
        start = i + needle.length();
      }
    }
    if (parts.isEmpty()) {
      return List.of(expression.trim());
    }
    parts.add(expression.substring(start).trim());
    return parts;
  }

  private static boolean balanced(String expression) {
    int depth = 0;
    for (int i = 0; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
        if (depth < 0) {
          return false;
        }
      }
    }
    return depth == 0;
  }
}
