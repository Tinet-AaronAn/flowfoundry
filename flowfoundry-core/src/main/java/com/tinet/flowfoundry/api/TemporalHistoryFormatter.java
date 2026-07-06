package com.tinet.flowfoundry.api;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.failure.v1.Failure;
import io.temporal.api.history.v1.HistoryEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TemporalHistoryFormatter {

  private TemporalHistoryFormatter() {}

  static List<Map<String, Object>> format(List<HistoryEvent> events) {
    List<Map<String, Object>> result = new ArrayList<>();
    if (events == null) {
      return result;
    }
    for (HistoryEvent event : events) {
      result.add(formatEvent(event));
    }
    return result;
  }

  private static Map<String, Object> formatEvent(HistoryEvent event) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("eventId", event.getEventId());
    map.put("eventType", event.getEventType().name());
    if (event.hasEventTime()) {
      map.put(
          "eventTime",
          Instant.ofEpochSecond(event.getEventTime().getSeconds(), event.getEventTime().getNanos())
              .toString());
    }
    map.putAll(formatAttributes(event));
    return map;
  }

  private static Map<String, Object> formatAttributes(HistoryEvent event) {
    return switch (event.getEventType()) {
      case EVENT_TYPE_WORKFLOW_EXECUTION_STARTED -> {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "workflowExecutionStartedEventAttributes");
        attrs.put(
            "workflowType",
            event.getWorkflowExecutionStartedEventAttributes().getWorkflowType().getName());
        yield attrs;
      }
      case EVENT_TYPE_WORKFLOW_EXECUTION_COMPLETED -> Map.of("type", "workflowExecutionCompletedEventAttributes");
      case EVENT_TYPE_WORKFLOW_EXECUTION_FAILED -> {
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "workflowExecutionFailedEventAttributes");
        attrs.put(
            "failure",
            failureToMap(event.getWorkflowExecutionFailedEventAttributes().getFailure()));
        yield attrs;
      }
      case EVENT_TYPE_ACTIVITY_TASK_SCHEDULED -> {
        var attributes = event.getActivityTaskScheduledEventAttributes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "activityTaskScheduledEventAttributes");
        attrs.put("activityId", attributes.getActivityId());
        attrs.put("activityType", attributes.getActivityType().getName());
        attrs.put("taskQueue", attributes.getTaskQueue().getName());
        yield attrs;
      }
      case EVENT_TYPE_ACTIVITY_TASK_STARTED -> {
        var attributes = event.getActivityTaskStartedEventAttributes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "activityTaskStartedEventAttributes");
        attrs.put("scheduledEventId", attributes.getScheduledEventId());
        yield attrs;
      }
      case EVENT_TYPE_ACTIVITY_TASK_COMPLETED -> {
        var attributes = event.getActivityTaskCompletedEventAttributes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "activityTaskCompletedEventAttributes");
        attrs.put("scheduledEventId", attributes.getScheduledEventId());
        yield attrs;
      }
      case EVENT_TYPE_ACTIVITY_TASK_FAILED -> {
        var attributes = event.getActivityTaskFailedEventAttributes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "activityTaskFailedEventAttributes");
        attrs.put("scheduledEventId", attributes.getScheduledEventId());
        attrs.put("failure", failureToMap(attributes.getFailure()));
        yield attrs;
      }
      case EVENT_TYPE_TIMER_FIRED -> {
        var attributes = event.getTimerFiredEventAttributes();
        Map<String, Object> attrs = new LinkedHashMap<>();
        attrs.put("type", "timerFiredEventAttributes");
        attrs.put("timerId", attributes.getTimerId());
        yield attrs;
      }
      case EVENT_TYPE_WORKFLOW_EXECUTION_TIMED_OUT ->
          Map.of("type", "workflowExecutionTimedOutEventAttributes");
      default -> Map.of("type", event.getEventType().name());
    };
  }

  static Map<String, Object> failureToMap(Failure failure) {
    if (failure == null || failure.getMessage().isBlank()) {
      return null;
    }
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("message", failure.getMessage());
    if (failure.hasCause()) {
      map.put("cause", failureToMap(failure.getCause()));
    }
    if (!failure.getStackTrace().isBlank()) {
      map.put("stackTrace", failure.getStackTrace());
    }
    if (failure.hasApplicationFailureInfo()) {
      Map<String, Object> info = new LinkedHashMap<>();
      info.put("type", failure.getApplicationFailureInfo().getType());
      map.put("applicationFailureInfo", info);
    }
    if (!failure.getSource().isBlank()) {
      map.put("source", failure.getSource());
    }
    return map;
  }

  static FailureDetails extractFailure(List<HistoryEvent> events) {
    if (events == null || events.isEmpty()) {
      return new FailureDetails("WORKFLOW_FAILED", null);
    }
    for (int i = events.size() - 1; i >= 0; i--) {
      HistoryEvent event = events.get(i);
      if (event.getEventType() == EventType.EVENT_TYPE_WORKFLOW_EXECUTION_FAILED) {
        Failure failure = event.getWorkflowExecutionFailedEventAttributes().getFailure();
        return new FailureDetails("WORKFLOW_FAILED", failure.getMessage());
      }
      if (event.getEventType() == EventType.EVENT_TYPE_ACTIVITY_TASK_FAILED) {
        Failure failure = event.getActivityTaskFailedEventAttributes().getFailure();
        return new FailureDetails("ACTIVITY_FAILED", failure.getMessage());
      }
    }
    return new FailureDetails("WORKFLOW_FAILED", null);
  }

  record FailureDetails(String type, String message) {}
}
