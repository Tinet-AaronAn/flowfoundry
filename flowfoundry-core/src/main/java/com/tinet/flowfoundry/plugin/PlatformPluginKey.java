package com.tinet.flowfoundry.plugin;

import java.io.Serializable;
import java.util.Objects;

/** Composite primary key (plugin id + version) for {@link PlatformPluginEntity}. */
public class PlatformPluginKey implements Serializable {

  private String id;
  private String version;

  public PlatformPluginKey() {}

  public PlatformPluginKey(String id, String version) {
    this.id = id;
    this.version = version;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof PlatformPluginKey other)) {
      return false;
    }
    return Objects.equals(id, other.id) && Objects.equals(version, other.version);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, version);
  }
}
