package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@Import({JacksonAutoConfiguration.class, AuditLogService.class})
class AuditLogServiceTest {

  @Autowired private AuditLogService auditLogService;

  @Test
  void excludesApiCallsUnlessRequested() {
    auditLogService.record(sample(AuditActions.API_CALL));
    auditLogService.record(sample(AuditActions.API_KEY_CREATED));

    var withoutApiCalls = auditLogService.search(null, null, null, null, false, null, 0, 10);
    assertThat(withoutApiCalls.items()).hasSize(1);
    assertThat(withoutApiCalls.items().get(0).action()).isEqualTo(AuditActions.API_KEY_CREATED);

    var withApiCalls = auditLogService.search(null, null, null, null, true, null, 0, 10);
    assertThat(withApiCalls.items()).hasSize(2);
  }

  @Test
  void paginatesResults() {
    for (int i = 0; i < 12; i++) {
      auditLogService.record(sample(AuditActions.API_KEY_UPDATED));
    }

    var firstPage = auditLogService.search(null, null, null, null, true, null, 0, 10);
    assertThat(firstPage.items()).hasSize(10);
    assertThat(firstPage.totalElements()).isEqualTo(12);
    assertThat(firstPage.totalPages()).isEqualTo(2);

    var secondPage = auditLogService.search(null, null, null, null, true, null, 1, 10);
    assertThat(secondPage.items()).hasSize(2);
  }

  @Test
  void filtersByTimeRange() {
    auditLogService.record(sampleAt(AuditActions.API_KEY_CREATED, Instant.parse("2026-07-01T10:00:00Z")));
    auditLogService.record(sampleAt(AuditActions.API_KEY_CREATED, Instant.parse("2026-07-03T10:00:00Z")));

    var result =
        auditLogService.search(
            null,
            null,
            Instant.parse("2026-07-02T00:00:00Z"),
            Instant.parse("2026-07-04T00:00:00Z"),
            true,
            null,
            0,
            10);

    assertThat(result.items()).hasSize(1);
    assertThat(result.items().get(0).occurredAt()).isEqualTo(Instant.parse("2026-07-03T10:00:00Z"));
  }

  private static AuditLogService.AuditLogEntry sample(String action) {
    return sampleAt(action, Instant.now());
  }

  private static AuditLogService.AuditLogEntry sampleAt(String action, Instant occurredAt) {
    return new AuditLogService.AuditLogEntry(
        occurredAt,
        "demo",
        "admin",
        action,
        "api_key",
        "demo",
        "default",
        "GET",
        "/api/demo",
        200,
        null,
        "127.0.0.1");
  }
}
