package com.tinet.flowfoundry.security;

public class AdminAccessDeniedException extends RuntimeException {

  public AdminAccessDeniedException() {
    super("Admin API is only available from 127.0.0.1");
  }
}
