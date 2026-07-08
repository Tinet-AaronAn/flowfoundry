package com.tinet.flowfoundry.flow;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FlowMetadata(String id, String name, String version) {}
