package com.tinet.flowfoundry.plugin.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PluginDownloadTokenServiceTest {

  @Test
  void validatesSignedToken() {
    PluginRuntimeProperties properties =
        new PluginRuntimeProperties(true, "ns", "img", "http://x", "secret", 1000, 30, "redis", null);
    PluginDownloadTokenService service = new PluginDownloadTokenService(properties);
    String token = service.token("demo", "1.0.0", "abc123");
    service.validate("demo", "1.0.0", "abc123", token);
    assertThatThrownBy(() -> service.validate("demo", "1.0.0", "abc123", "bad"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
