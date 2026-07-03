package com.tinet.flowfoundary.activity;

import java.util.Map;

/** Business-scenario Activity routing implemented in flowfoundry-app/modules/*. */
public interface BusinessActivityRouter {

  boolean supports(String activityType);

  Object execute(String activityType, Map<String, Object> input);
}
