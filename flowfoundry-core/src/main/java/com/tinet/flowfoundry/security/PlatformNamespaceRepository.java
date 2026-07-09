package com.tinet.flowfoundry.security;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlatformNamespaceRepository extends JpaRepository<PlatformNamespaceEntity, String> {

  @Query("SELECT n.id FROM PlatformNamespaceEntity n ORDER BY n.id")
  List<String> findAllIds();
}
