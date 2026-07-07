package com.tinet.flowfoundry.security;

public class ApiClientNotFoundException extends RuntimeException {

  public ApiClientNotFoundException(String clientId) {
    super("API client not found: " + clientId);
  }
}
