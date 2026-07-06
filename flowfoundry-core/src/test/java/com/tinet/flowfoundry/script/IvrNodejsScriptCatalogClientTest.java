package com.tinet.flowfoundry.script;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class IvrNodejsScriptCatalogClientTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void parsesWrappedScriptList() throws Exception {
    var root =
        objectMapper.readTree(
            """
            {
              "result": [
                {
                  "codeId": "877",
                  "name": "risk-check",
                  "description": "Risk routing",
                  "activeVersion": "3",
                  "latestPublishedVersion": "3",
                  "published": true
                }
              ]
            }
            """);

    assertThat(IvrNodejsScriptCatalogClient.parseScripts(root))
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.scriptCodeId()).isEqualTo("877");
              assertThat(entry.scriptName()).isEqualTo("risk-check");
              assertThat(entry.activeVersion()).isEqualTo("3");
              assertThat(entry.published()).isTrue();
            });
  }

  @Test
  void parsesVersionListWithActiveFlag() throws Exception {
    var root =
        objectMapper.readTree(
            """
            {
              "versions": [
                { "version": "1", "published": true, "active": false },
                { "version": "3", "published": true, "active": true, "label": "V3" }
              ]
            }
            """);

    assertThat(IvrNodejsScriptCatalogClient.parseVersions(root))
        .extracting(ScriptVersionEntry::version, ScriptVersionEntry::active)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("1", false),
            org.assertj.core.groups.Tuple.tuple("3", true));
  }
}
