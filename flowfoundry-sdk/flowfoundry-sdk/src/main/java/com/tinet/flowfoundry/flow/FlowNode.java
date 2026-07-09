package com.tinet.flowfoundry.flow;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.List;
import java.util.Map;

public record FlowNode(
    String id,
    String kind,
    String name,
    String canvasKind,
    String activityType,
    String taskQueue,
    String timeout,
    Integer maxAttempts,
    @JsonAlias("decisionRef") String scriptCodeId,
    @JsonAlias("decisionVersion") String scriptVersion,
    String scriptName,
    List<String> inputArgs,
    Map<String, String> inputMapping,
    Map<String, String> outputMapping,
    String inputMappingMode,
    Map<String, Object> config) {}
