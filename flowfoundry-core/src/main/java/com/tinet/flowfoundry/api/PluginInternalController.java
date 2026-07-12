package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.plugin.PluginAdminService;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginDto;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginPageDto;
import com.tinet.flowfoundry.plugin.PluginContracts.ScalePluginRequest;
import com.tinet.flowfoundry.plugin.PluginStorageService;
import com.tinet.flowfoundry.plugin.PlatformPluginEntity;
import com.tinet.flowfoundry.plugin.PlatformPluginKey;
import com.tinet.flowfoundry.plugin.PlatformPluginRepository;
import com.tinet.flowfoundry.plugin.runtime.PluginDownloadTokenService;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal plugin package download for runner initContainers (token auth only). */
@RestController
@RequestMapping("/api/internal/plugins")
public class PluginInternalController {

  private final PlatformPluginRepository pluginRepository;
  private final PluginStorageService storageService;
  private final PluginDownloadTokenService downloadTokenService;

  public PluginInternalController(
      PlatformPluginRepository pluginRepository,
      PluginStorageService storageService,
      PluginDownloadTokenService downloadTokenService) {
    this.pluginRepository = pluginRepository;
    this.storageService = storageService;
    this.downloadTokenService = downloadTokenService;
  }

  @GetMapping("/{id}/{version}/package")
  public ResponseEntity<byte[]> downloadPackage(
      @PathVariable String id,
      @PathVariable String version,
      @RequestHeader(value = "X-Plugin-Download-Token", required = false) String token) {
    PlatformPluginEntity entity =
        pluginRepository
            .findById(new PlatformPluginKey(id, version))
            .orElseThrow(() -> new IllegalArgumentException("Plugin not found: " + id + ":" + version));
    downloadTokenService.validate(id, version, entity.getJarSha256(), token);
    byte[] content = storageService.read(id, version);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"" + id + "-" + version + ".jar\"")
        .body(content);
  }
}
