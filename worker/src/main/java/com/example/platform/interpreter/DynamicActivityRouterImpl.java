package com.example.platform.interpreter;

import com.example.platform.callcampaign.CallCampaignActivities;
import com.example.platform.callcampaign.model.SupervisorReviewRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DynamicActivityRouterImpl implements DynamicActivityRouter {

  private final CallCampaignActivities callCampaignActivities;
  private final ObjectMapper objectMapper;
  private final String dmnServiceUrl;

  public DynamicActivityRouterImpl(
      CallCampaignActivities callCampaignActivities,
      ObjectMapper objectMapper,
      @Value("${platform.dmn.service-url:}") String dmnServiceUrl) {
    this.callCampaignActivities = callCampaignActivities;
    this.objectMapper = objectMapper;
    this.dmnServiceUrl = dmnServiceUrl;
  }

  @Override
  public Object execute(String activityType, Map<String, Object> input) {
    Map<String, Object> safeInput = input == null ? Map.of() : input;
    return switch (activityType) {
      case "load-campaign" -> callCampaignActivities.loadCampaign(stringValue(safeInput, "campaignId", 0));
      case "prepare-call-round" ->
          callCampaignActivities.prepareCallRound(
              stringValue(safeInput, "campaignId", 0), intValue(safeInput, "roundNumber", 1));
      case "execute-call-round" ->
          callCampaignActivities.executeCallRound(
              stringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              stringValue(safeInput, "batchId", 2));
      case "wait-round-completion" ->
          callCampaignActivities.waitRoundCompletion(
              stringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              stringValue(safeInput, "dialerTaskId", 2));
      case "aggregate-round-results" ->
          callCampaignActivities.aggregateRoundResults(
              stringValue(safeInput, "campaignId", 0), intValue(safeInput, "roundNumber", 1));
      case "evaluate-next-round" ->
          callCampaignActivities.evaluateNextRound(
              stringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              intValue(safeInput, "remainingContacts", 2),
              intValue(safeInput, "maxRounds", 3));
      case "dmn-decision" -> executeDmnDecision(safeInput);
      case "supervisor-review" ->
          callCampaignActivities.supervisorReview(
              new SupervisorReviewRequest(
                  stringValue(safeInput, "campaignId", 0),
                  intValue(safeInput, "roundNumber", 1),
                  stringValue(safeInput, "assigneeRole", 2, "supervisor")));
      case "finalize-campaign" ->
          callCampaignActivities.finalizeCampaign(
              stringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "totalRoundsExecuted", 1));
      default -> throw new IllegalArgumentException("Unknown dynamic activity type: " + activityType);
    };
  }

  private Map<String, Object> executeDmnDecision(Map<String, Object> input) {
    Object rawConfig = input.get("_config");
    Map<?, ?> config = rawConfig instanceof Map<?, ?> map ? map : Map.of();
    Object decisionRef = config.get("decisionRef");
    Object decisionVersion = config.get("decisionVersion");
    Map<String, Object> request = new LinkedHashMap<>();
    request.put("decisionRef", decisionRef == null ? "unknown" : decisionRef);
    request.put("decisionVersion", decisionVersion == null ? "latest" : decisionVersion);
    request.put("input", input);
    if (dmnServiceUrl != null && !dmnServiceUrl.isBlank()) {
      return callExternalDmnService(request);
    }
    return Map.of(
        "decisionRef", String.valueOf(decisionRef == null ? "unknown" : decisionRef),
        "decisionVersion", String.valueOf(decisionVersion == null ? "latest" : decisionVersion),
        "matched", true,
        "output", input);
  }

  private Map<String, Object> callExternalDmnService(Map<String, Object> requestBody) {
    try {
      String body = objectMapper.writeValueAsString(requestBody);
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(dmnServiceUrl))
              .timeout(Duration.ofSeconds(10))
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(body))
              .build();
      HttpResponse<String> response =
          HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "DMN service returned " + response.statusCode() + ": " + response.body());
      }
      return objectMapper.readValue(response.body(), new TypeReference<>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to call DMN service: " + dmnServiceUrl, e);
    }
  }

  private static String stringValue(Map<String, Object> input, String key, int argIndex) {
    return stringValue(input, key, argIndex, null);
  }

  private static String stringValue(
      Map<String, Object> input, String key, int argIndex, String defaultValue) {
    Object value = value(input, key, argIndex);
    return value == null ? defaultValue : String.valueOf(value);
  }

  private static int intValue(Map<String, Object> input, String key, int argIndex) {
    Object value = value(input, key, argIndex);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      throw new IllegalArgumentException("Missing integer input: " + key);
    }
    return Integer.parseInt(String.valueOf(value));
  }

  private static Object value(Map<String, Object> input, String key, int argIndex) {
    if (input.containsKey(key)) {
      return input.get(key);
    }
    Object args = input.get("_args");
    if (args instanceof List<?> list && list.size() > argIndex) {
      return list.get(argIndex);
    }
    if (args instanceof Object[] array && array.length > argIndex) {
      return array[argIndex];
    }
    return null;
  }
}
