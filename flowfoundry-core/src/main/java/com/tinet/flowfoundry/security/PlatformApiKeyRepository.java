package com.tinet.flowfoundry.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformApiKeyRepository extends JpaRepository<PlatformApiKeyEntity, String> {

  Optional<PlatformApiKeyEntity> findByKeyHashAndStatus(String keyHash, String status);

  Optional<PlatformApiKeyEntity> findByKeyHash(String keyHash);

  boolean existsByKeyHash(String keyHash);

  @Query(
      """
      SELECT COUNT(DISTINCT k) FROM PlatformApiKeyEntity k
      JOIN k.namespaces n
      WHERE n = :namespace
      """)
  long countByNamespace(@Param("namespace") String namespace);
}
