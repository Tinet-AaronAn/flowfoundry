package com.tinet.flowfoundry.security;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformApiClientRepository extends JpaRepository<PlatformApiClientEntity, String> {

  Optional<PlatformApiClientEntity> findByKeyHashAndStatus(String keyHash, String status);

  Optional<PlatformApiClientEntity> findByKeyHash(String keyHash);

  boolean existsByKeyHash(String keyHash);
}
