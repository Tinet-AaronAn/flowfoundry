package com.tinet.flowfoundry.plugin;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformPluginRepository
    extends JpaRepository<PlatformPluginEntity, PlatformPluginKey> {

  List<PlatformPluginEntity> findByIdOrderByCreatedAtDesc(String id);

  List<PlatformPluginEntity> findByNamespace(String namespace);

  List<PlatformPluginEntity> findByDesiredState(String desiredState);
}
