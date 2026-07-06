package com.tinet.flowfoundry.interpreter.runtime;

import java.lang.reflect.Array;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VariableStore {

  private static final Pattern SEGMENT_PATTERN = Pattern.compile("^([^\\[\\]]*)(?:\\[([^\\]]*)\\])?$");

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
    if (path.contains(".") || path.contains("[")) {
      Object fromVars = readPath(vars, path);
      if (fromVars != null) {
        return fromVars;
      }
      Object fromInput = readPath(input, path);
      if (fromInput != null) {
        return fromInput;
      }
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

  public VariableStore copy() {
    VariableStore copy = new VariableStore(new LinkedHashMap<>(input));
    copy.vars.putAll(vars);
    copy.lastResult = lastResult;
    return copy;
  }

  public void mergeFrom(VariableStore other) {
    if (other == null) {
      return;
    }
    vars.putAll(other.vars);
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

  private static List<PathSegment> parsePathSegments(String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    List<PathSegment> segments = new ArrayList<>();
    for (String raw : path.split("\\.", -1)) {
      if (raw.isEmpty()) {
        throw new IllegalArgumentException("Invalid path: " + path);
      }
      segments.add(PathSegment.parse(raw));
    }
    return segments;
  }

  private static Object readPath(Object root, String path) {
    if (root == null || path == null || path.isBlank()) {
      return root;
    }
    Object current = root;
    for (PathSegment segment : parsePathSegments(path)) {
      current = readSegment(current, segment);
      if (current == null) {
        return null;
      }
    }
    return current;
  }

  @SuppressWarnings("unchecked")
  private static void writePath(Map<String, Object> root, String path, Object value) {
    List<PathSegment> segments = parsePathSegments(path);
    if (segments.isEmpty()) {
      throw new IllegalArgumentException("Assignment target path is required");
    }
    Object current = root;
    for (int i = 0; i < segments.size() - 1; i++) {
      current = ensureWritableSegment(current, segments.get(i));
    }
    writeSegment(current, segments.get(segments.size() - 1), value);
  }

  private static Object readSegment(Object current, PathSegment segment) {
    if (current == null) {
      return null;
    }
    Object next = current;
    if (segment.hasKey()) {
      next = readProperty(next, segment.key());
    }
    if (segment.hasIndex()) {
      next = readIndex(next, segment.index());
    }
    return next;
  }

  @SuppressWarnings("unchecked")
  private static Object ensureWritableSegment(Object current, PathSegment segment) {
    if (current == null) {
      throw new IllegalArgumentException("Cannot assign through null path segment");
    }
    Object next = current;
    if (segment.hasKey()) {
      if (!(next instanceof Map<?, ?> map)) {
        throw new IllegalArgumentException("Cannot navigate property '" + segment.key() + "' on non-map value");
      }
      Map<String, Object> writable = (Map<String, Object>) next;
      Object child = writable.get(segment.key());
      if (child == null) {
        child = segment.hasIndex() ? new ArrayList<>() : new LinkedHashMap<String, Object>();
        writable.put(segment.key(), child);
      }
      next = child;
    }
    if (segment.hasIndex()) {
      next = ensureListElement(next, segment.index(), true);
    }
    return next;
  }

  @SuppressWarnings("unchecked")
  private static void writeSegment(Object current, PathSegment segment, Object value) {
    if (current == null) {
      throw new IllegalArgumentException("Cannot assign to null path target");
    }
    if (segment.hasKey() && segment.hasIndex()) {
      Map<String, Object> map = requireMap(current, "indexed property");
      List<Object> list = ensureList(map, segment.key());
      setListIndex(list, segment.index(), value);
      return;
    }
    if (segment.hasKey()) {
      requireMap(current, "property").put(segment.key(), value);
      return;
    }
    if (segment.hasIndex()) {
      setListIndex(requireList(current, "index"), segment.index(), value);
      return;
    }
    throw new IllegalArgumentException("Invalid assignment path segment");
  }

  private static Object readProperty(Object current, String key) {
    if (current instanceof Map<?, ?> map) {
      return map.get(key);
    }
    if (current instanceof Record) {
      return readRecordComponent(current, key);
    }
    return readBeanAccessor(current, key);
  }

  private static Object readIndex(Object current, int index) {
    if (current instanceof List<?> list) {
      int resolved = resolveIndexForRead(index, list.size());
      return resolved < 0 ? null : list.get(resolved);
    }
    if (current != null && current.getClass().isArray()) {
      int length = Array.getLength(current);
      int resolved = resolveIndexForRead(index, length);
      return resolved < 0 ? null : Array.get(current, resolved);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> ensureList(Map<String, Object> map, String key) {
    Object current = map.get(key);
    if (current instanceof List<?> list) {
      return (List<Object>) list;
    }
    if (current != null) {
      throw new IllegalArgumentException("Cannot index non-list property: " + key);
    }
    List<Object> list = new ArrayList<>();
    map.put(key, list);
    return list;
  }

  @SuppressWarnings("unchecked")
  private static Object ensureListElement(Object current, int index, boolean createNestedMap) {
    List<Object> list = requireList(current, "path segment");
    if (index < 0) {
      int resolved = resolveIndexForWrite(index, list.size());
      Object element = list.get(resolved);
      if (element == null && createNestedMap) {
        element = new LinkedHashMap<String, Object>();
        list.set(resolved, element);
      }
      return element;
    }
    while (list.size() <= index) {
      list.add(null);
    }
    Object element = list.get(index);
    if (element == null && createNestedMap) {
      element = new LinkedHashMap<String, Object>();
      list.set(index, element);
    }
    return element;
  }

  @SuppressWarnings("unchecked")
  private static void setListIndex(List<Object> list, int index, Object value) {
    if (index < 0) {
      list.set(resolveIndexForWrite(index, list.size()), value);
      return;
    }
    while (list.size() <= index) {
      list.add(null);
    }
    list.set(index, value);
  }

  /** Python-style negative index for read; returns -1 when out of bounds. */
  private static int resolveIndexForRead(int index, int size) {
    if (size == 0) {
      return -1;
    }
    int resolved = index >= 0 ? index : size + index;
    return resolved >= 0 && resolved < size ? resolved : -1;
  }

  /** Python-style negative index for write; negative indices cannot extend the list. */
  private static int resolveIndexForWrite(int index, int size) {
    if (index >= 0) {
      return index;
    }
    if (size == 0) {
      throw new IllegalArgumentException("Array index out of bounds: " + index);
    }
    int resolved = size + index;
    if (resolved < 0 || resolved >= size) {
      throw new IllegalArgumentException("Array index out of bounds: " + index);
    }
    return resolved;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> requireMap(Object current, String context) {
    if (!(current instanceof Map<?, ?> map)) {
      throw new IllegalArgumentException("Cannot assign " + context + " on non-map value");
    }
    return (Map<String, Object>) map;
  }

  @SuppressWarnings("unchecked")
  private static List<Object> requireList(Object current, String context) {
    if (!(current instanceof List<?> list)) {
      throw new IllegalArgumentException("Cannot assign " + context + " on non-list value");
    }
    return (List<Object>) list;
  }

  private static int parseIndex(String raw) {
    if (raw == null || raw.isBlank() || !raw.matches("-?\\d+")) {
      throw new IllegalArgumentException("Invalid array index: " + raw);
    }
    try {
      return Integer.parseInt(raw);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid array index: " + raw, e);
    }
  }

  private record PathSegment(String key, Integer index) {

    static PathSegment parse(String raw) {
      Matcher matcher = SEGMENT_PATTERN.matcher(raw.trim());
      if (!matcher.matches()) {
        throw new IllegalArgumentException("Invalid path segment: " + raw);
      }
      String key = matcher.group(1);
      String indexText = matcher.group(2);
      Integer index = indexText == null ? null : parseIndex(indexText);
      if ((key == null || key.isEmpty()) && index == null) {
        throw new IllegalArgumentException("Invalid path segment: " + raw);
      }
      return new PathSegment(key == null || key.isEmpty() ? null : key, index);
    }

    boolean hasKey() {
      return key != null && !key.isEmpty();
    }

    boolean hasIndex() {
      return index != null;
    }
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
