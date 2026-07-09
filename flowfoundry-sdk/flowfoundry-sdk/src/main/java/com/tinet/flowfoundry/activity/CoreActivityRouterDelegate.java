package com.tinet.flowfoundry.activity;

import java.util.Map;

/** Optional bridge to platform core activities (script-runtime, human-task) when flowfoundry-core is present. */
public interface CoreActivityRouterDelegate {

  boolean supports(String activityType);

  Object execute(String activityType, Map<String, Object> input);
}
