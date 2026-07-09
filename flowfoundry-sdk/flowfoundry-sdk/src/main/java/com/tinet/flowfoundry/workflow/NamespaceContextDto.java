package com.tinet.flowfoundry.workflow;

import java.util.Set;

/** Active namespace context for the current request (workflows / run logs / api keys scope). */
public record NamespaceContextDto(
    String namespace, Set<String> allowedNamespaces, String namespaceHeader) {}
