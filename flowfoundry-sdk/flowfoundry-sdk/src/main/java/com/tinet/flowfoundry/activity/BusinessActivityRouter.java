package com.tinet.flowfoundry.activity;

import java.util.Map;

/** Business-scenario Activity routing implemented in examples/ or independent business App modules. */
public interface BusinessActivityRouter {

  boolean supports(String activityType);

  Object execute(String activityType, Map<String, Object> input);
}
