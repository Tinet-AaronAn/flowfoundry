package com.tinet.flowfoundry.plugin.runtime;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/** HMAC token used by plugin runner initContainers to download packages from the platform. */
@Service
public class PluginDownloadTokenService {

  private final PluginRuntimeProperties properties;

  public PluginDownloadTokenService(PluginRuntimeProperties properties) {
    this.properties = properties;
  }

  public String token(String pluginId, String version, String jarSha256) {
    return hmac(pluginId + "|" + version + "|" + jarSha256);
  }

  public void validate(String pluginId, String version, String jarSha256, String providedToken) {
    if (providedToken == null || providedToken.isBlank()) {
      throw new IllegalArgumentException("Plugin download token is required");
    }
    String expected = token(pluginId, version, jarSha256);
    if (!expected.equals(providedToken.trim())) {
      throw new IllegalArgumentException("Invalid plugin download token");
    }
  }

  private String hmac(String payload) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              properties.resolvedDownloadTokenSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder builder = new StringBuilder(digest.length * 2);
      for (byte value : digest) {
        builder.append(String.format("%02x", value));
      }
      return builder.toString();
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("Failed to sign plugin download token", e);
    }
  }
}
