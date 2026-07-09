package com.tinet.flowfoundry.client;

import com.tinet.flowfoundry.config.ConditionalOnFlowFoundryWorker;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URI;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

/**
 * App BFF: forwards browser requests on {@code flowfoundry.bff.base-path} to the platform API
 * using server-side credentials from {@link FlowFoundryPlatformProperties}.
 */
@RestController
@RequestMapping("${flowfoundry.bff.base-path:/app/api/flowfoundry}")
@ConditionalOnFlowFoundryWorker
@ConditionalOnProperty(prefix = "flowfoundry.bff", name = "enabled", havingValue = "true", matchIfMissing = true)
public class FlowFoundryPlatformBffController {

  private final RestClient restClient;
  private final FlowFoundryBffProperties bffProperties;
  private final FlowFoundryPlatformProperties platformProperties;

  public FlowFoundryPlatformBffController(
      FlowFoundryBffProperties bffProperties, FlowFoundryPlatformProperties platformProperties) {
    this.bffProperties = bffProperties;
    this.platformProperties = platformProperties;
    this.restClient = DefaultFlowFoundryPlatformClient.buildRestClient(platformProperties);
  }

  @RequestMapping("/**")
  public ResponseEntity<byte[]> proxy(HttpServletRequest request, @RequestBody(required = false) byte[] body)
      throws IOException {
    String apiPath = toPlatformApiPath(request);
    HttpMethod method = HttpMethod.valueOf(request.getMethod());
    byte[] payload = body != null ? body : StreamUtils.copyToByteArray(request.getInputStream());

    RestClient.RequestBodySpec spec =
        restClient
            .method(method)
            .uri(URI.create(platformProperties.baseUrl() + apiPath + queryString(request)))
            .headers(headers -> copyForwardHeaders(request, headers));

    RestClient.ResponseSpec response =
        payload.length == 0 && !allowsEmptyBody(method)
            ? spec.retrieve()
            : spec.body(payload).retrieve();

    ResponseEntity<byte[]> entity = response.toEntity(byte[].class);
    HttpHeaders responseHeaders = new HttpHeaders();
    entity.getHeaders().forEach((name, values) -> {
      if (!isHopByHopHeader(name)) {
        responseHeaders.put(name, values);
      }
    });
    return new ResponseEntity<>(entity.getBody(), responseHeaders, entity.getStatusCode());
  }

  private String toPlatformApiPath(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String prefix = bffProperties.basePath();
    if (!uri.startsWith(prefix)) {
      throw new IllegalStateException("BFF path mismatch: " + uri);
    }
    String suffix = uri.substring(prefix.length());
    if (suffix.isEmpty()) {
      return "/api";
    }
    return "/api" + suffix;
  }

  private static String queryString(HttpServletRequest request) {
    String query = request.getQueryString();
    return query == null || query.isBlank() ? "" : "?" + query;
  }

  private void copyForwardHeaders(HttpServletRequest request, HttpHeaders headers) {
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (isHopByHopHeader(name) || isBlockedHeader(name)) {
        continue;
      }
      List<String> values = Collections.list(request.getHeaders(name));
      headers.put(name, values);
    }
    if (!platformProperties.apiKey().isBlank()) {
      headers.set(FlowFoundryHttpHeaders.API_KEY, platformProperties.apiKey());
    }
    String namespace = request.getHeader(FlowFoundryHttpHeaders.PLATFORM_NAMESPACE);
    if (namespace != null && !namespace.isBlank()) {
      headers.set(FlowFoundryHttpHeaders.PLATFORM_NAMESPACE, namespace);
    } else if (!platformProperties.namespace().isBlank()) {
      headers.set(FlowFoundryHttpHeaders.PLATFORM_NAMESPACE, platformProperties.namespace());
    }
  }

  private static boolean allowsEmptyBody(HttpMethod method) {
    return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
  }

  private static boolean isBlockedHeader(String name) {
    return FlowFoundryHttpHeaders.API_KEY.equalsIgnoreCase(name);
  }

  private static boolean isHopByHopHeader(String name) {
    return "host".equalsIgnoreCase(name)
        || "connection".equalsIgnoreCase(name)
        || "content-length".equalsIgnoreCase(name)
        || "transfer-encoding".equalsIgnoreCase(name);
  }
}
