package com.tinet.flowfoundry.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.activity.ScriptRuntimeProperties;
import org.junit.jupiter.api.Test;

class ScriptCatalogServiceTest {

  @Test
  void returnsStubCatalogWhenRemoteIsDisabled() {
    ScriptCatalogService service =
        new ScriptCatalogService(
            new IvrNodejsScriptCatalogClient(
                new ObjectMapper(),
                new ScriptCatalogProperties("", null, null, null),
                new ScriptRuntimeProperties("", 30, "7000001", "flowfoundry")),
            new ScriptRuntimeProperties("", 30, "7000001", "flowfoundry"));

    assertThat(service.listScripts(null).source()).isEqualTo("stub");
    assertThat(service.listScripts(null).scripts())
        .extracting(ScriptCatalogEntry::scriptCodeId)
        .contains("risk-check", "demo-script");
    assertThat(service.listVersions("risk-check", null).versions())
        .extracting(ScriptVersionEntry::version)
        .containsExactly("1");
  }
}
