package com.tinet.flowfoundary.activity;

import com.tinet.flowfoundary.interpreter.DynamicActivityRouter;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Single Temporal Activity entry point: core activities first, then business module routers.
 */
@Component
public class CompositeDynamicActivityRouter implements DynamicActivityRouter {

  private final CoreActivityRouter coreActivityRouter;
  private final List<BusinessActivityRouter> businessRouters;

  public CompositeDynamicActivityRouter(
      CoreActivityRouter coreActivityRouter, List<BusinessActivityRouter> businessRouters) {
    this.coreActivityRouter = coreActivityRouter;
    this.businessRouters = businessRouters == null ? List.of() : businessRouters;
  }

  @Override
  public Object execute(String activityType, Map<String, Object> input) {
    if (coreActivityRouter.supports(activityType)) {
      return coreActivityRouter.execute(activityType, input);
    }
    for (BusinessActivityRouter router : businessRouters) {
      if (router.supports(activityType)) {
        return router.execute(activityType, input);
      }
    }
    throw new IllegalArgumentException("Unknown dynamic activity type: " + activityType);
  }
}
