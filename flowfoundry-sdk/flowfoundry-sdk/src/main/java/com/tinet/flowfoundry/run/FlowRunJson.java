package com.tinet.flowfoundry.run;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class FlowRunJson {

  private static final int MAX_DETAIL_BYTES = 8192;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private FlowRunJson() {}

  public static String toJson(Map<String, Object> detail) {
    if (detail == null || detail.isEmpty()) {
      return null;
    }
    try {
      return truncate(MAPPER.writeValueAsString(detail));
    } catch (JsonProcessingException e) {
      return truncate(String.valueOf(detail));
    }
  }

  public static String toJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return truncate(MAPPER.writeValueAsString(value));
    } catch (JsonProcessingException e) {
      return truncate(String.valueOf(value));
    }
  }

  public static String truncate(String value) {
    if (value == null || value.isBlank()) {
      return value;
    }
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    if (bytes.length <= MAX_DETAIL_BYTES) {
      return value;
    }
    StringBuilder builder = new StringBuilder();
    int used = 0;
    for (int offset = 0; offset < value.length(); ) {
      int codePoint = value.codePointAt(offset);
      int charCount = Character.charCount(codePoint);
      String piece = value.substring(offset, offset + charCount);
      int pieceBytes = piece.getBytes(StandardCharsets.UTF_8).length;
      if (used + pieceBytes > MAX_DETAIL_BYTES - 3) {
        break;
      }
      builder.append(piece);
      used += pieceBytes;
      offset += charCount;
    }
    return builder + "...";
  }

  public static String namespaceFromBusinessKey(String businessKey) {
    if (businessKey == null || businessKey.isBlank()) {
      return "default";
    }
    int idx = businessKey.indexOf(':');
    return idx > 0 ? businessKey.substring(0, idx) : "default";
  }
}
