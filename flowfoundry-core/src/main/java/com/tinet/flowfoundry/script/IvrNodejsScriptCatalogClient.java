package com.tinet.flowfoundry.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tinet.flowfoundry.activity.ScriptRuntimeProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Calls CTICloud IVR Node.js script management APIs for catalog lookup. */
@Component
public class IvrNodejsScriptCatalogClient {

  private final ObjectMapper objectMapper;
  private final ScriptCatalogProperties catalogProperties;
  private final ScriptRuntimeProperties runtimeProperties;
  private final HttpClient httpClient = HttpClient.newHttpClient();

  public IvrNodejsScriptCatalogClient(
      ObjectMapper objectMapper,
      ScriptCatalogProperties catalogProperties,
      ScriptRuntimeProperties runtimeProperties) {
    this.objectMapper = objectMapper;
    this.catalogProperties = catalogProperties;
    this.runtimeProperties = runtimeProperties;
  }

  public boolean isRemoteEnabled() {
    return catalogProperties.enabled();
  }

  public List<ScriptCatalogEntry> listScripts(String enterpriseId) {
    Map<String, String> query = baseQuery(enterpriseId);
    JsonNode body = exchange(catalogProperties.resolvedScriptsPath(), query, Map.of());
    return parseScripts(body);
  }

  public List<ScriptVersionEntry> listVersions(String scriptCodeId, String enterpriseId) {
    Map<String, String> query = baseQuery(enterpriseId);
    query.put("codeId", scriptCodeId);
    query.put("jsCodeId", scriptCodeId);
    query.put("tinetJsCodeId", scriptCodeId);
    String path =
        catalogProperties
            .resolvedVersionsPath()
            .replace("{scriptCodeId}", encode(scriptCodeId))
            .replace("{codeId}", encode(scriptCodeId));
    JsonNode body = exchange(path, query, Map.of("codeId", scriptCodeId));
    return parseVersions(body);
  }

  private Map<String, String> baseQuery(String enterpriseId) {
    Map<String, String> query = new LinkedHashMap<>();
    if (enterpriseId != null && !enterpriseId.isBlank()) {
      query.put("enterpriseId", enterpriseId);
      query.put("tinetEnterpriseId", enterpriseId);
    }
    return query;
  }

  private JsonNode exchange(String path, Map<String, String> query, Map<String, Object> jsonBody) {
    if (!catalogProperties.enabled()) {
      throw new IllegalStateException("Script catalog base URL is not configured");
    }
    try {
      String method = catalogProperties.resolvedHttpMethod();
      HttpRequest.Builder builder =
          HttpRequest.newBuilder()
              .timeout(Duration.ofSeconds(runtimeProperties.resolvedTimeoutSeconds()))
              .header("Accept", "application/json");
      if ("POST".equals(method)) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.putAll(jsonBody);
        query.forEach(body::putIfAbsent);
        builder
            .uri(URI.create(buildUrl(path, Map.of())))
            .header("Content-Type", "application/json")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
      } else {
        builder.uri(URI.create(buildUrl(path, query))).GET();
      }
      HttpResponse<String> response =
          httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() < 200 || response.statusCode() >= 300) {
        throw new IllegalStateException(
            "Script catalog request failed with "
                + response.statusCode()
                + ": "
                + response.body());
      }
      if (response.body() == null || response.body().isBlank()) {
        return objectMapper.nullNode();
      }
      return objectMapper.readTree(response.body());
    } catch (IllegalStateException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to call script catalog service", e);
    }
  }

  private String buildUrl(String path, Map<String, String> query) {
    String base = catalogProperties.baseUrl().trim();
    String normalizedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    StringBuilder url = new StringBuilder(normalizedBase).append(normalizedPath);
    if (!query.isEmpty()) {
      url.append("?");
      boolean first = true;
      for (Map.Entry<String, String> entry : query.entrySet()) {
        if (!first) {
          url.append("&");
        }
        url.append(encode(entry.getKey())).append("=").append(encode(entry.getValue()));
        first = false;
      }
    }
    return url.toString();
  }

  static List<ScriptCatalogEntry> parseScripts(JsonNode root) {
    List<ScriptCatalogEntry> scripts = new ArrayList<>();
    for (JsonNode item : arrayItems(root, "scripts", "list", "result", "data", "items")) {
      String scriptCodeId = firstText(item, "codeId", "jsCodeId", "tinetJsCodeId", "scriptCodeId", "id");
      if (scriptCodeId == null) {
        continue;
      }
      scripts.add(
          new ScriptCatalogEntry(
              scriptCodeId,
              firstText(item, "name", "scriptName", "tinetScriptName", "title"),
              firstText(item, "description", "desc", "remark"),
              firstText(
                  item,
                  "activeVersion",
                  "effectiveVersion",
                  "currentVersion",
                  "version",
                  "publishVersion"),
              firstText(item, "latestPublishedVersion", "latestVersion", "maxVersion"),
              firstBoolean(item, "published", "publishStatus", "isPublished")));
    }
    return scripts;
  }

  static List<ScriptVersionEntry> parseVersions(JsonNode root) {
    List<ScriptVersionEntry> versions = new ArrayList<>();
    for (JsonNode item : arrayItems(root, "versions", "list", "result", "data", "items")) {
      String version =
          firstText(item, "version", "jsCodeVersion", "tinetJsCodeVersion", "versionNumber");
      if (version == null) {
        continue;
      }
      versions.add(
          new ScriptVersionEntry(
              version,
              firstBoolean(item, "published", "publishStatus", "isPublished"),
              firstBoolean(item, "active", "effective", "isActive", "current"),
              firstText(item, "label", "name", "description")));
    }
    return versions;
  }

  private static Iterable<JsonNode> arrayItems(JsonNode root, String... keys) {
    if (root == null || root.isNull()) {
      return List.of();
    }
    if (root.isArray()) {
      return root::elements;
    }
    for (String key : keys) {
      JsonNode candidate = root.get(key);
      if (candidate != null && candidate.isArray()) {
        return candidate::elements;
      }
    }
    return List.of();
  }

  private static String firstText(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.get(key);
      if (value != null && !value.isNull() && !value.asText().isBlank()) {
        return value.asText();
      }
    }
    return null;
  }

  private static boolean firstBoolean(JsonNode node, String... keys) {
    for (String key : keys) {
      JsonNode value = node.get(key);
      if (value == null || value.isNull()) {
        continue;
      }
      if (value.isBoolean()) {
        return value.booleanValue();
      }
      String text = value.asText("");
      if ("published".equalsIgnoreCase(text) || "true".equalsIgnoreCase(text) || "1".equals(text)) {
        return true;
      }
      if ("false".equalsIgnoreCase(text) || "0".equals(text) || "draft".equalsIgnoreCase(text)) {
        return false;
      }
    }
    return false;
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
