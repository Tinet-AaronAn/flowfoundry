package com.tinet.flowfoundry.interpreter.runtime;

public enum RunSource {
  WEB_MODELER("web-modeler"),
  PRODUCTION("production");

  private final String wireValue;

  RunSource(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public boolean usesStubActivities() {
    return this == WEB_MODELER;
  }

  public static RunSource fromWire(String raw) {
    if (raw != null && WEB_MODELER.wireValue.equalsIgnoreCase(raw.trim())) {
      return WEB_MODELER;
    }
    return PRODUCTION;
  }
}
