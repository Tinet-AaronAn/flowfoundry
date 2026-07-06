package com.tinet.flowfoundry.interpreter.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RunSourceResolverTest {

  @Test
  void acceptsWebModelerOnlyWithTrustedClientHeader() {
    assertThat(RunSourceResolver.resolve("web-modeler", "web-modeler"))
        .isEqualTo(RunSource.WEB_MODELER);
  }

  @Test
  void forcesProductionWhenClientHeaderMissing() {
    assertThat(RunSourceResolver.resolve("web-modeler", null))
        .isEqualTo(RunSource.PRODUCTION);
  }

  @Test
  void forcesProductionWhenRunSourceMissing() {
    assertThat(RunSourceResolver.resolve(null, "web-modeler"))
        .isEqualTo(RunSource.PRODUCTION);
  }

  @Test
  void forcesProductionForExternalApiCallers() {
    assertThat(RunSourceResolver.resolve("web-modeler", "curl"))
        .isEqualTo(RunSource.PRODUCTION);
    assertThat(RunSourceResolver.resolve("production", "web-modeler"))
        .isEqualTo(RunSource.PRODUCTION);
  }
}
