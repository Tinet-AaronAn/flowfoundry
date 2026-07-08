package com.tinet.flowfoundry.security;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalhostAdminFilterTest {

  @Test
  void allowsLocalhostAdminRequests() throws ServletException, IOException {
    PlatformSecurityProperties properties = new PlatformSecurityProperties();
    properties.setAdminLocalhostOnly(true);
    LocalhostAdminFilter filter = new LocalhostAdminFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/admin/api-keys");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
  MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isNotNull();
  }

  @Test
  void blocksRemoteAdminRequests() throws ServletException, IOException {
    PlatformSecurityProperties properties = new PlatformSecurityProperties();
    properties.setAdminLocalhostOnly(true);
    LocalhostAdminFilter filter = new LocalhostAdminFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/admin/api-keys");
    request.setRemoteAddr("10.0.0.8");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertThat(response.getStatus()).isEqualTo(403);
  }

  @Test
  void ignoresNonAdminPaths() throws ServletException, IOException {
    PlatformSecurityProperties properties = new PlatformSecurityProperties();
    properties.setAdminLocalhostOnly(true);
    LocalhostAdminFilter filter = new LocalhostAdminFilter(properties);

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/workflows");
    request.setRemoteAddr("10.0.0.8");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(chain.getRequest()).isNotNull();
  }
}
