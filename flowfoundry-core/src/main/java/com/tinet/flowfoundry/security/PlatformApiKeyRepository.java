package com.tinet.flowfoundry.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformApiKeyRepository extends JpaRepository<PlatformApiKeyEntity, String> {

  Optional<PlatformApiKeyEntity> findByKeyHashAndStatus(String keyHash, String status);

  Optional<PlatformApiKeyEntity> findByKeyHash(String keyHash);

  boolean existsByKeyHash(String keyHash);
}
