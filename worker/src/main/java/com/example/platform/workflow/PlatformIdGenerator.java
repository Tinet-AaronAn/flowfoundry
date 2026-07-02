package com.example.platform.workflow;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class PlatformIdGenerator {

  private static final Map<String, String> PREFIXES =
      Map.of(
          "workflow", "workflow_",
          "event", "event_",
          "subprocess", "subprocess_",
          "task", "task_",
          "gateway", "gateway_",
          "participant", "participant_");

  private static final Set<String> SUPPORTED_KINDS = PREFIXES.keySet();

  private final ShortIdGenerator shortIdGenerator;
  private final PlatformIdRegistryRepository registryRepository;

  public PlatformIdGenerator(
      ShortIdGenerator shortIdGenerator, PlatformIdRegistryRepository registryRepository) {
    this.shortIdGenerator = shortIdGenerator;
    this.registryRepository = registryRepository;
  }

  public String allocate(String kind) {
    String normalized = normalizeKind(kind);
    String prefix = PREFIXES.get(normalized);
    for (int attempt = 0; attempt < 32; attempt++) {
      String id = prefix + shortIdGenerator.generate();
      try {
        registryRepository.save(new PlatformIdRegistryEntity(id, normalized, Instant.now()));
        return id;
      } catch (DataIntegrityViolationException ignored) {
        // retry on collision
      }
    }
    throw new IllegalStateException("Failed to allocate unique id for kind: " + normalized);
  }

  public String allocateWorkflowId() {
    return allocate("workflow");
  }

  public static String normalizeKind(String kind) {
    if (kind == null || kind.isBlank()) {
      throw new IllegalArgumentException("kind is required");
    }
    String normalized = kind.trim().toLowerCase(Locale.ROOT);
    if (!SUPPORTED_KINDS.contains(normalized)) {
      throw new IllegalArgumentException("Unsupported id kind: " + kind);
    }
    return normalized;
  }

  public static Set<String> supportedKinds() {
    return SUPPORTED_KINDS;
  }
}
