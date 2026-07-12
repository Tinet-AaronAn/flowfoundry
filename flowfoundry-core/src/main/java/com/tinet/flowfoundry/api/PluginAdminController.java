package com.tinet.flowfoundry.api;

import com.tinet.flowfoundry.plugin.PluginAdminService;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginDto;
import com.tinet.flowfoundry.plugin.PluginContracts.PluginPageDto;
import com.tinet.flowfoundry.plugin.PluginContracts.ScalePluginRequest;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/plugins")
public class PluginAdminController {

  private final PluginAdminService pluginAdminService;

  public PluginAdminController(PluginAdminService pluginAdminService) {
    this.pluginAdminService = pluginAdminService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public PluginDto upload(@RequestParam("file") MultipartFile file) {
    try {
      return pluginAdminService.upload(file.getBytes());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read uploaded plugin package", e);
    }
  }

  @GetMapping
  public PluginPageDto list() {
    return pluginAdminService.list();
  }

  @GetMapping("/{id}/{version}")
  public PluginDto get(@PathVariable String id, @PathVariable String version) {
    return pluginAdminService.get(id, version);
  }

  @PutMapping("/{id}/scale")
  public PluginDto scale(@PathVariable String id, @RequestBody ScalePluginRequest request) {
    return pluginAdminService.scale(id, request);
  }

  @PostMapping("/{id}/{version}/start")
  public PluginDto start(@PathVariable String id, @PathVariable String version) {
    return pluginAdminService.start(id, version);
  }

  @PostMapping("/{id}/{version}/stop")
  public PluginDto stop(@PathVariable String id, @PathVariable String version) {
    return pluginAdminService.stop(id, version);
  }

  @PostMapping("/{id}/{version}/reload")
  public PluginDto reload(@PathVariable String id, @PathVariable String version) {
    return pluginAdminService.reload(id, version);
  }

  @GetMapping("/{id}/{version}/logs")
  public String logs(
      @PathVariable String id,
      @PathVariable String version,
      @RequestParam(defaultValue = "500") int tail) {
    return pluginAdminService.logs(id, version, tail);
  }

  @DeleteMapping("/{id}/{version}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id, @PathVariable String version) {
    pluginAdminService.delete(id, version);
  }
}
