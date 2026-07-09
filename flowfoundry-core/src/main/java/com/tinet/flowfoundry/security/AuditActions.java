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

  private AuditActions() {}
}
