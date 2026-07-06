package com.tinet.flowfoundry.interpreter.runtime;

public enum InputMappingMode {
  MAPPED_ONLY("mapped-only"),
  PASSTHROUGH_UNMAPPED("passthrough-unmapped");

  private final String wireValue;

  InputMappingMode(String wireValue) {
    this.wireValue = wireValue;
  }

  public String wireValue() {
    return wireValue;
  }

  public static InputMappingMode fromWire(String value) {
    if (MAPPED_ONLY.wireValue.equals(value)) {
      return MAPPED_ONLY;
    }
    return PASSTHROUGH_UNMAPPED;
  }
}
