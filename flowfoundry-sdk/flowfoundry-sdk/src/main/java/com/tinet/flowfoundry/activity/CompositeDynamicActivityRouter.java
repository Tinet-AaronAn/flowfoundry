package com.tinet.flowfoundry.activity;

import com.tinet.flowfoundry.interpreter.DynamicActivityRouter;
import com.tinet.flowfoundry.interpreter.model.FlowFoundryTrace;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Single Temporal Activity entry point: core activities first, then business module routers.
 */
@Component
public class CompositeDynamicActivityRouter implements DynamicActivityRouter {

  private static final Logger log = LoggerFactory.getLogger(CompositeDynamicActivityRouter.class);

  private final CoreActivityRouterDelegate coreActivityRouter;
  private final List<BusinessActivityRouter> businessRouters;

  public CompositeDynamicActivityRouter(
      @Autowired(required = false) CoreActivityRouterDelegate coreActivityRouter,
      List<BusinessActivityRouter> businessRouters) {
    this.coreActivityRouter = coreActivityRouter;
    this.businessRouters = businessRouters == null ? List.of() : businessRouters;
  }

  @Override
  public Object execute(String activityType, Map<String, Object> input) {
    logActivityStart(activityType, input);
    if (coreActivityRouter != null && coreActivityRouter.supports(activityType)) {
      return coreActivityRouter.execute(activityType, input);
    }
    for (BusinessActivityRouter router : businessRouters) {
      if (router.supports(activityType)) {
        return router.execute(activityType, input);
      }
    }
    throw new IllegalArgumentException("Unknown dynamic activity type: " + activityType);
  }

  private static void logActivityStart(String activityType, Map<String, Object> input) {
    FlowFoundryTrace trace = FlowFoundryTrace.fromInput(input);
    if (trace.nodeId() == null) {
      log.info("Executing activity type={}", activityType);
      return;
    }
    log.info(
        "Executing activity type={} nodeId={} nodeName={} canvasKind={}",
        activityType,
        trace.nodeId(),
        trace.nodeName(),
        trace.canvasKind());
  }
}
