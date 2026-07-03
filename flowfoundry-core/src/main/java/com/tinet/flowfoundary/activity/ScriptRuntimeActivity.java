package com.tinet.flowfoundary.activity;

import com.tinet.flowfoundary.interpreter.runtime.ActivityExecutionContext;
import com.tinet.flowfoundary.interpreter.runtime.DualModeActivityHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Calls the internal Node.js script runtime service for Script Task nodes.
 * Runs in flowfoundry-core; business modules do not re-implement this.
 */
@Component
public class ScriptRuntimeActivity extends DualModeActivityHandler {

  private final ObjectMapper objectMapper;
  private final String serviceUrl;

  @Autowired
  public ScriptRuntimeActivity(ObjectMapper objectMapper, ScriptRuntimeProperties properties) {
    this.objectMapper = objectMapper;
    this.serviceUrl = properties.serviceUrl() == null ? "" : properties.serviceUrl().trim();
  }

  public Map<String, Object> execute(Map<String, Object> input) {
    Map<String, Object> safeInput = input == null ? Map.of() : input;
    Object rawConfig = safeInput.get("_config");
    Map<?, ?> config = rawConfig instanceof Map<?, ?> map ? map : Map.of();
    String scriptRef = stringValue(config.get("decisionRef"), "unknown");
    String scriptVersion = stringValue(config.get("decisionVersion"), "latest");

    if (ActivityExecutionContext.from(safeInput).usesStubActivities()) {
      return stubResult(scriptRef, scriptVersion, safeInput);
    }

    Map<String, Object> request = new LinkedHashMap<>();
    request.put("scriptRef", scriptRef);
    request.put("scriptVersion", scriptVersion);
    request.put("decisionRef", scriptRef);
    request.put("decisionVersion", scriptVersion);
    request.put("input", safeInput);

    if (!serviceUrl.isBlank()) {
      return callScriptRuntimeService(request);
    }
    return stubResult(scriptRef, scriptVersion, safeInput);
  }

  private Map<String, Object> stubResult(
      String scriptRef, String scriptVersion, Map<String, Object> input) {
    return Map.of(
        "scriptRef", scriptRef,
        "scriptVersion", scriptVersion,
        "decisionRef", scriptRef,
        "decisionVersion", scriptVersion,
        "matched", true,
        "output", input);
  }

  private Map<String, Object> callScriptRuntimeService(Map<String, Object> requestBody) {
    try {
      String body = objectMapper.writeValueAsString(requestBody);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(serviceUrl))
              .timeout(Duration.ofSeconds(30))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Script runtime service returned "
                + response.statusCode()
                + ": "
                + response.body());
      }
      return objectMapper.readValue(response.body(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to call script runtime service: " + serviceUrl, e);
    }
  }

  private static String stringValue(Object value, String fallback) {
    if (value == null || String.valueOf(value).isBlank()) {
      return fallback;
    }
    return String.valueOf(value);
  }
}
