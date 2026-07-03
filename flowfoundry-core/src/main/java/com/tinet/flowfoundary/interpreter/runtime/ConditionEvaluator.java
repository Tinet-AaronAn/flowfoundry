package com.example.platform.interpreter.runtime;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ConditionEvaluator {

  private static final Pattern COMPARISON =
      Pattern.compile("^(.+?)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");

  public boolean evaluate(Object condition, VariableStore variables) {
    if (condition == null) {
      return false;
    }
    if (condition instanceof Map<?, ?> map) {
      return evaluateMap(map, variables);
    }
    return evaluate(String.valueOf(condition), variables);
  }

  public boolean evaluate(String expression, VariableStore variables) {
    if (expression == null || expression.isBlank()) {
      return false;
    }
    String normalized = unwrapExpression(expression.trim());
    if ("default".equalsIgnoreCase(normalized)) {
      return false;
    }
    return evaluateOr(normalized, variables);
  }

  private boolean evaluateMap(Map<?, ?> condition, VariableStore variables) {
    Object ast = condition.containsKey("ast") ? condition.get("ast") : condition;
    if (!(ast instanceof Map<?, ?> astMap)) {
      throw new IllegalArgumentException("Safe FEEL condition requires AST object");
    }
    return truthy(evaluateAst(astMap, variables));
  }

  @SuppressWarnings("unchecked")
  private Object evaluateAst(Map<?, ?> ast, VariableStore variables) {
    String op = String.valueOf(ast.get("op"));
    return switch (op) {
      case "and" -> ((List<Object>) astArgs(ast)).stream()
          .allMatch(arg -> truthy(evaluateAst(castMap(arg), variables)));
      case "or" -> ((List<Object>) astArgs(ast)).stream()
          .anyMatch(arg -> truthy(evaluateAst(castMap(arg), variables)));
      case "not" -> !truthy(evaluateAst(castMap(ast.get("arg")), variables));
      case "truthy" -> resolveAstValue(ast.get("arg"), variables);
      case "=", "==", "!=", ">", "<", ">=", "<=" -> {
        Object left = resolveAstValue(ast.get("left"), variables);
        Object right = resolveAstValue(ast.get("right"), variables);
        yield compare(left, "==".equals(op) ? "=" : op, right);
      }
      default -> throw new IllegalArgumentException("Unsupported Safe FEEL operator: " + op);
    };
  }

  private static List<?> astArgs(Map<?, ?> ast) {
    Object args = ast.get("args");
    return args instanceof List<?> list ? list : List.of();
  }

  private static Map<?, ?> castMap(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map;
    }
    throw new IllegalArgumentException("Safe FEEL AST node must be object: " + value);
  }

  private Object resolveAstValue(Object value, VariableStore variables) {
    if (value instanceof Map<?, ?> map && map.containsKey("var")) {
      String variable = String.valueOf(map.get("var"));
      if (variable.startsWith("input.")
          || variable.startsWith("vars.")
          || variable.startsWith("lastResult")) {
        return variables.resolve("$." + variable);
      }
      return variables.resolve(variable);
    }
    return value;
  }

  private static boolean truthy(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return value != null;
  }

  private boolean evaluateOr(String expression, VariableStore variables) {
    for (String term : split(expression, " or ")) {
      if (evaluateAnd(term, variables)) {
        return true;
      }
    }
    return false;
  }

  private boolean evaluateAnd(String expression, VariableStore variables) {
    for (String term : split(expression, " and ")) {
      if (!evaluateAtom(term.trim(), variables)) {
        return false;
      }
    }
    return true;
  }

  private boolean evaluateAtom(String expression, VariableStore variables) {
    String atom = stripParentheses(expression.trim());
    if (atom.startsWith("not ")) {
      return !evaluateAtom(atom.substring(4), variables);
    }
    Matcher matcher = COMPARISON.matcher(atom);
    if (!matcher.matches()) {
      Object value = resolveOperand(atom, variables);
      if (value instanceof Boolean bool) {
        return bool;
      }
      if (value == null) {
        return false;
      }
      throw new IllegalArgumentException("Condition is not boolean: " + expression);
    }
    Object left = resolveOperand(matcher.group(1), variables);
    Object right = resolveOperand(matcher.group(3), variables);
    return compare(left, matcher.group(2), right);
  }

  private static boolean compare(Object left, String operator, Object right) {
    if ("==".equals(operator) || "=".equals(operator)) {
      return equalsValue(left, right);
    }
    if ("!=".equals(operator)) {
      return !equalsValue(left, right);
    }
    if (left == null || right == null) {
      return false;
    }
    int comparison = compareOrdered(left, right);
    return switch (operator) {
      case ">" -> comparison > 0;
      case "<" -> comparison < 0;
      case ">=" -> comparison >= 0;
      case "<=" -> comparison <= 0;
      default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
    };
  }

  private static boolean equalsValue(Object left, Object right) {
    if (left == null || right == null) {
      return left == right;
    }
    if (isNumber(left) && isNumber(right)) {
      return toDecimal(left).compareTo(toDecimal(right)) == 0;
    }
    return left.equals(right);
  }

  @SuppressWarnings("unchecked")
  private static int compareOrdered(Object left, Object right) {
    if (isNumber(left) && isNumber(right)) {
      return toDecimal(left).compareTo(toDecimal(right));
    }
    if (left instanceof Comparable comparable && left.getClass().isInstance(right)) {
      return comparable.compareTo(right);
    }
    return String.valueOf(left).compareTo(String.valueOf(right));
  }

  private Object resolveOperand(String raw, VariableStore variables) {
    String value = stripParentheses(raw.trim());
    String lower = value.toLowerCase(Locale.ROOT);
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
    return variables.resolve(value);
  }

  private static boolean isNumber(Object value) {
    return value instanceof Number || value instanceof BigDecimal;
  }

  private static BigDecimal toDecimal(Object value) {
    if (value instanceof BigDecimal decimal) {
      return decimal;
    }
    if (value instanceof Number number) {
      return new BigDecimal(number.toString());
    }
    return new BigDecimal(String.valueOf(value));
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

  private static List<String> split(String expression, String delimiter) {
    return List.of(expression.split("(?i)" + Pattern.quote(delimiter)));
  }
}
