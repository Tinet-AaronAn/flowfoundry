package com.tinet.flowfoundary.interpreter.runtime;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Delegates Activity execution to real or stub implementations based on {@link
 * ActivityExecutionContext#usesStubActivities()}.
 */
public abstract class DualModeActivityHandler {

  protected <T> T executeDual(Map<String, Object> input, Supplier<T> real, Supplier<T> stub) {
    return ActivityExecutionContext.from(input).usesStubActivities() ? stub.get() : real.get();
  }

  protected void executeDualVoid(
      Map<String, Object> input, Runnable real, Runnable stub) {
    if (ActivityExecutionContext.from(input).usesStubActivities()) {
      stub.run();
    } else {
      real.run();
    }
  }
}
