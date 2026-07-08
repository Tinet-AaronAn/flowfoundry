package com.tinet.flowfoundry.security;

public class ApiKeyNotFoundException extends RuntimeException {

  public ApiKeyNotFoundException(String apiKeyId) {
    super("API key not found: " + apiKeyId);
  }
}
