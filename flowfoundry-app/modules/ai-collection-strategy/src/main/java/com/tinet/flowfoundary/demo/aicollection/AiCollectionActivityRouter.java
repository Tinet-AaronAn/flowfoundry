package com.tinet.flowfoundary.demo.aicollection;

import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundary.activity.BusinessActivityRouter;
import com.tinet.flowfoundary.interpreter.runtime.DualModeActivityHandler;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class AiCollectionActivityRouter extends DualModeActivityHandler
    implements BusinessActivityRouter {

  private static final Set<String> SUPPORTED =
      Set.of(
          "import-numbers",
          "filter-and-split-batches",
          "notify-owner-report",
          "load-campaign",
          "prepare-call-round",
          "execute-call-round",
          "wait-round-completion",
          "start-ai-tagging",
          "wait-tagging-completion",
          "filter-next-round",
          "aggregate-round-results",
          "evaluate-next-round",
          "supervisor-review",
          "finalize-campaign");

  private final CallCampaignActivities realActivities;
  private final CallCampaignActivities stubActivities;

  @Autowired
  public AiCollectionActivityRouter(
      @Qualifier("callCampaignActivitiesImpl") CallCampaignActivities realActivities,
      @Qualifier("callCampaignActivitiesStub") CallCampaignActivities stubActivities) {
    this.realActivities = realActivities;
    this.stubActivities = stubActivities;
  }

  static AiCollectionActivityRouter forTests(
      CallCampaignActivities realActivities, CallCampaignActivities stubActivities) {
    return new AiCollectionActivityRouter(realActivities, stubActivities);
  }

  @Override
  public boolean supports(String activityType) {
    return SUPPORTED.contains(activityType);
  }

  @Override
  public Object execute(String activityType, Map<String, Object> input) {
    Map<String, Object> safeInput = input == null ? Map.of() : input;
    CallCampaignActivities activities = selectActivities(safeInput);
    return switch (activityType) {
      case "import-numbers" ->
          activities.importNumbers(requireStringValue(safeInput, "campaignId", 0));
      case "filter-and-split-batches" ->
          activities.filterAndSplitBatches(requireStringValue(safeInput, "campaignId", 0));
      case "notify-owner-report" ->
          activities.notifyOwnerReport(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "batchCount", 1),
              intValue(safeInput, "eligibleContacts", 2));
      case "load-campaign" ->
          activities.loadCampaign(requireStringValue(safeInput, "campaignId", 0));
      case "prepare-call-round" ->
          activities.prepareCallRound(
              requireStringValue(safeInput, "campaignId", 0), intValue(safeInput, "roundNumber", 1));
      case "execute-call-round" ->
          activities.executeCallRound(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              stringValue(safeInput, "batchId", 2, "stub-batch"));
      case "wait-round-completion" ->
          activities.waitRoundCompletion(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              stringValue(safeInput, "dialerTaskId", 2, "stub-dialer"));
      case "start-ai-tagging" ->
          activities.startAiTagging(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              intValue(safeInput, "recordingCount", 2, 0));
      case "wait-tagging-completion" ->
          activities.waitTaggingCompletion(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              stringValue(safeInput, "taggingJobId", 2, "stub-tagging"));
      case "filter-next-round" ->
          activities.filterNextRound(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              intValue(safeInput, "remainingContacts", 2),
              intValue(safeInput, "maxRounds", 3));
      case "aggregate-round-results" ->
          activities.aggregateRoundResults(
              requireStringValue(safeInput, "campaignId", 0), intValue(safeInput, "roundNumber", 1));
      case "evaluate-next-round" ->
          activities.evaluateNextRound(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "roundNumber", 1),
              intValue(safeInput, "remainingContacts", 2),
              intValue(safeInput, "maxRounds", 3));
      case "supervisor-review" ->
          activities.supervisorReview(
              new SupervisorReviewRequest(
                  requireStringValue(safeInput, "campaignId", 0),
                  intValue(safeInput, "roundNumber", 1),
                  stringValue(safeInput, "assigneeRole", 2, "supervisor")));
      case "finalize-campaign" ->
          activities.finalizeCampaign(
              requireStringValue(safeInput, "campaignId", 0),
              intValue(safeInput, "totalRoundsExecuted", 1));
      default -> throw new IllegalArgumentException("Unknown dynamic activity type: " + activityType);
    };
  }

  private CallCampaignActivities selectActivities(Map<String, Object> input) {
    return executeDual(input, () -> realActivities, () -> stubActivities);
  }

  private static String requireStringValue(Map<String, Object> input, String key, int argIndex) {
    String value = stringValue(input, key, argIndex, null);
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Missing required input: " + key);
    }
    return value;
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
    return intValue(input, key, argIndex, null);
  }

  private static int intValue(
      Map<String, Object> input, String key, int argIndex, Integer defaultValue) {
    Object value = value(input, key, argIndex);
    if (value instanceof Number number) {
      return number.intValue();
    }
    if (value == null) {
      if (defaultValue != null) {
        return defaultValue;
      }
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
