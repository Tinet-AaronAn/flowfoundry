package com.tinet.flowfoundry.config;

import java.util.Optional;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Component;

@Component
public class StaticAssetVersion {

  private final Optional<BuildProperties> buildProperties;

  public StaticAssetVersion(Optional<BuildProperties> buildProperties) {
    this.buildProperties = buildProperties;
  }

  public String value() {
    return buildProperties
        .map(BuildProperties::getTime)
        .map(time -> String.valueOf(time.toEpochMilli()))
        .orElse("dev");
  }
}
