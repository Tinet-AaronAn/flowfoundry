package com.tinet.flowfoundry.activity;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import com.tinet.flowfoundry.interpreter.runtime.ActivityExecutionContext;
import com.tinet.flowfoundry.interpreter.runtime.DualModeActivityHandler;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Executes Script Task nodes by calling the ivr-nodejs service ({@code POST /ivr/nodejs/run}).
 * Field mapping follows ivr-nodejs: {@code tinetJsCodeId}, {@code tinetJsCodeVersion}, etc.
 */
@Component
public class ScriptRuntimeActivity extends DualModeActivityHandler {

  private static final Set<String> RESERVED_INPUT_KEYS =
      Set.of(
          "_config",
          ActivityExecutionContext.CONTEXT_KEY,
          "_args",
          FlowFoundryTrace.INPUT_KEY);

  private static final Set<String> IVR_NODEJS_CONTROL_KEYS =
      Set.of(
          "tinetJsCodeId",
          "tinetJsCodeVersion",
          "tinetEnterpriseId",
          "tinetReqUniqueId",
          "tinetUniqueId",
          "tinetDebug",
          "tinetEnableScriptLog",
          "tinetSourceType",
          "tinetScriptName",
          "tinetRunTimeout",
          "tinetDoAsync");

  private final ObjectMapper objectMapper;
  private final String serviceUrl;
  private final int timeoutSeconds;
  private final int runTimeoutMs;
  private final String defaultEnterpriseId;
  private final String sourceType;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Autowired
  public ScriptRuntimeActivity(ObjectMapper objectMapper, ScriptRuntimeProperties properties) {
    this.objectMapper = objectMapper;
    this.serviceUrl = properties.serviceUrl() == null ? "" : properties.serviceUrl().trim();
    this.timeoutSeconds = properties.resolvedTimeoutSeconds();
    this.runTimeoutMs = properties.resolvedRunTimeoutMs();
    this.defaultEnterpriseId =
        properties.enterpriseId() == null ? "" : properties.enterpriseId().trim();
    this.sourceType = properties.resolvedSourceType();
  }

  public Map<String, Object> execute(Map<String, Object> input) {
    Map<String, Object> safeInput = input == null ? Map.of() : input;
    ScriptBinding binding = resolveScriptBinding(safeInput);
    Map<String, Object> scriptInputs = extractScriptInputs(safeInput);

    return executeDual(
        safeInput,
        () -> executeProduction(binding, scriptInputs, safeInput),
        () -> stubResult(binding, scriptInputs));
  }

  private Map<String, Object> executeProduction(
      ScriptBinding binding, Map<String, Object> scriptInputs, Map<String, Object> safeInput) {
    if (serviceUrl.isBlank()) {
      throw new IllegalStateException(
          "Script runtime service URL is not configured "
              + "(activity.script-runtime.service-url / SCRIPT_RUNTIME_SERVICE_URL)");
    }
    String enterpriseId = resolveEnterpriseId(binding, scriptInputs);
    if (enterpriseId.isBlank()) {
      throw new IllegalStateException(
          "Script runtime requires enterpriseId "
              + "(activity.script-runtime.enterprise-id / SCRIPT_RUNTIME_ENTERPRISE_ID, "
              + "node config.enterpriseId, or input enterpriseId)");
    }
    String reqUniqueId = resolveReqUniqueId(safeInput);
    Map<String, Object> request =
        buildIvrNodejsRequest(
            binding.scriptCodeId(),
            binding.scriptVersion(),
            enterpriseId,
            reqUniqueId,
            binding.scriptName(),
            sourceType,
            runTimeoutMs,
            scriptInputs);
    Map<String, Object> response = callIvrNodejsService(request);
    return normalizeIvrNodejsResponse(binding, response);
  }

  static Map<String, Object> extractScriptInputs(Map<String, Object> activityInput) {
    Map<String, Object> inputs = new LinkedHashMap<>();
    activityInput.forEach(
        (key, value) -> {
          if (!RESERVED_INPUT_KEYS.contains(key) && !IVR_NODEJS_CONTROL_KEYS.contains(key)) {
            inputs.put(key, value);
          }
        });
    return inputs;
  }

  static Map<String, Object> buildIvrNodejsRequest(
      String scriptCodeId,
      String scriptVersion,
      String enterpriseId,
      String reqUniqueId,
      String scriptName,
      String sourceType,
      int runTimeoutMs,
      Map<String, Object> scriptInputs) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tinetJsCodeId", scriptCodeId);
    body.put("tinetJsCodeVersion", scriptVersion);
    body.put("tinetEnterpriseId", enterpriseId);
    body.put("tinetReqUniqueId", reqUniqueId);
    body.put("tinetRunTimeout", runTimeoutMs);
    if (sourceType != null && !sourceType.isBlank()) {
      body.put("tinetSourceType", sourceType);
    }
    if (scriptName != null && !scriptName.isBlank()) {
      body.put("tinetScriptName", scriptName);
    }
    if (scriptInputs != null) {
      scriptInputs.forEach(body::putIfAbsent);
    }
    return body;
  }

  static Map<String, Object> normalizeIvrNodejsResponse(
      ScriptBinding binding, Map<String, Object> body) {
    Map<String, Object> normalized = new LinkedHashMap<>();
    normalized.put("scriptCodeId", binding.scriptCodeId());
    normalized.put("scriptVersion", binding.scriptVersion());
    if (binding.scriptName() != null && !binding.scriptName().isBlank()) {
      normalized.put("scriptName", binding.scriptName());
    }
    normalized.put("matched", true);
    if (body != null) {
      body.forEach(
          (key, value) -> {
            if (!IVR_NODEJS_CONTROL_KEYS.contains(key)) {
              normalized.put(key, value);
            }
          });
      normalized.put("output", new LinkedHashMap<>(normalized));
    } else {
      normalized.put("output", Map.of());
    }
    return normalized;
  }

  private Map<String, Object> stubResult(ScriptBinding binding, Map<String, Object> scriptInputs) {
    Map<String, Object> result = new LinkedHashMap<>(scriptInputs);
    result.putIfAbsent("matched", true);
    result.putIfAbsent("nextAction", "continue");
    return normalizeIvrNodejsResponse(binding, result);
  }

  private Map<String, Object> callIvrNodejsService(Map<String, Object> requestBody) {
    try {
      String body = objectMapper.writeValueAsString(requestBody);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(resolveRunUrl()))
              .timeout(Duration.ofSeconds(timeoutSeconds))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "ivr-nodejs returned " + response.statusCode() + ": " + response.body());
      }
      String responseBody = response.body();
      if (responseBody == null || responseBody.isBlank()) {
        return Map.of();
      }
      return objectMapper.readValue(responseBody, new TypeReference<>() {});
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to call ivr-nodejs service: " + resolveRunUrl(), e);
    }
  }

  private String resolveRunUrl() {
    if (serviceUrl.endsWith("/ivr/nodejs/run")) {
      return serviceUrl;
    }
    String base =
        serviceUrl.endsWith("/") ? serviceUrl.substring(0, serviceUrl.length() - 1) : serviceUrl;
    return base + "/ivr/nodejs/run";
  }

  private String resolveEnterpriseId(ScriptBinding binding, Map<String, Object> scriptInputs) {
    if (binding.enterpriseId() != null && !binding.enterpriseId().isBlank()) {
      return binding.enterpriseId();
    }
    Object fromInput = scriptInputs.get("enterpriseId");
    if (fromInput != null && !String.valueOf(fromInput).isBlank()) {
      return String.valueOf(fromInput);
    }
    return defaultEnterpriseId;
  }

  private static String resolveReqUniqueId(Map<String, Object> safeInput) {
    ActivityExecutionContext context = ActivityExecutionContext.from(safeInput);
    if (context.workflowId() != null && !context.workflowId().isBlank()) {
      return context.workflowId();
    }
    if (context.businessKey() != null && !context.businessKey().isBlank()) {
      return context.businessKey();
    }
    return UUID.randomUUID().toString();
  }

  private ScriptBinding resolveScriptBinding(Map<String, Object> safeInput) {
    Map<?, ?> config = configMap(safeInput.get("_config"));
    return new ScriptBinding(
        firstNonBlank(config.get("scriptCodeId"), config.get("decisionRef"), "unknown"),
        firstNonBlank(config.get("scriptVersion"), config.get("decisionVersion"), "latest"),
        stringValueOrNull(config.get("scriptName")),
        stringValueOrNull(config.get("enterpriseId")));
  }

  @SuppressWarnings("unchecked")
  private static Map<?, ?> configMap(Object rawConfig) {
    return rawConfig instanceof Map<?, ?> map ? map : Map.of();
  }

  private static String firstNonBlank(Object primary, Object legacy, String fallback) {
    String value = stringValueOrNull(primary);
    if (value != null) {
      return value;
    }
    value = stringValueOrNull(legacy);
    if (value != null) {
      return value;
    }
    return fallback;
  }

  private static String stringValueOrNull(Object value) {
    if (value == null || String.valueOf(value).isBlank()) {
      return null;
    }
    return String.valueOf(value);
  }

  private record ScriptBinding(
      String scriptCodeId, String scriptVersion, String scriptName, String enterpriseId) {}
}
