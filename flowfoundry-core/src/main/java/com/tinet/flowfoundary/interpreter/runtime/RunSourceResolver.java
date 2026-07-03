package com.tinet.flowfoundary.interpreter.runtime;

/**
 * Resolves the effective run source for a flow execution.
 *
 * <p>Web modeler runs are accepted only when both the request body and the trusted client header
 * indicate {@code web-modeler}. All other callers are forced to {@code production}, regardless of
 * any runSource value supplied by the client.
 */
public final class RunSourceResolver {

  public static final String WEB_MODELER_CLIENT_HEADER = "X-FlowFoundry-Client";
  public static final String WEB_MODELER_CLIENT_VALUE = "web-modeler";

  private RunSourceResolver() {}

  public static RunSource resolve(String requestedRunSource, String clientHeader) {
    if (WEB_MODELER_CLIENT_VALUE.equalsIgnoreCase(safe(clientHeader))
        && RunSource.WEB_MODELER.wireValue().equalsIgnoreCase(safe(requestedRunSource))) {
      return RunSource.WEB_MODELER;
    }
    return RunSource.PRODUCTION;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }
}
