package com.tinet.flowfoundry.security;

public final class AuditActions {

  public static final String API_CALL = "API_CALL";
  public static final String AUTH_FAILED = "AUTH_FAILED";
  public static final String API_KEY_CREATED = "API_KEY_CREATED";
  public static final String API_KEY_UPDATED = "API_KEY_UPDATED";
  public static final String API_KEY_DISABLED = "API_KEY_DISABLED";
  public static final String API_KEY_DELETED = "API_KEY_DELETED";
  public static final String API_KEY_ROTATED = "API_KEY_ROTATED";
  public static final String NAMESPACE_CREATED = "NAMESPACE_CREATED";
  public static final String NAMESPACE_UPDATED = "NAMESPACE_UPDATED";
  public static final String NAMESPACE_DELETED = "NAMESPACE_DELETED";
  public static final String TEMPORAL_NAMESPACE_REGISTERED = "TEMPORAL_NAMESPACE_REGISTERED";
  public static final String TEMPORAL_CLUSTER_CREATED = "TEMPORAL_CLUSTER_CREATED";
  public static final String TEMPORAL_CLUSTER_UPDATED = "TEMPORAL_CLUSTER_UPDATED";
  public static final String TEMPORAL_CLUSTER_DELETED = "TEMPORAL_CLUSTER_DELETED";
  public static final String TEMPORAL_SCHEDULE_PAUSED = "TEMPORAL_SCHEDULE_PAUSED";
  public static final String TEMPORAL_SCHEDULE_RESUMED = "TEMPORAL_SCHEDULE_RESUMED";
  public static final String PLUGIN_UPLOADED = "PLUGIN_UPLOADED";
  public static final String PLUGIN_SCALED = "PLUGIN_SCALED";
  public static final String PLUGIN_STARTED = "PLUGIN_STARTED";
  public static final String PLUGIN_STOPPED = "PLUGIN_STOPPED";
  public static final String PLUGIN_RELOADED = "PLUGIN_RELOADED";
  public static final String PLUGIN_DELETED = "PLUGIN_DELETED";

  private AuditActions() {}
}
