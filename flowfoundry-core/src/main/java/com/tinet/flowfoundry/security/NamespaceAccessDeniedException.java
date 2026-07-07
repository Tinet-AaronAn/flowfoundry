package com.tinet.flowfoundry.security;

public class NamespaceAccessDeniedException extends RuntimeException {

  public NamespaceAccessDeniedException(String namespace) {
    super("Access denied for namespace: " + namespace);
  }
}
