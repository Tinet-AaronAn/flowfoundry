package com.tinet.flowfoundry.script;

public record ScriptCatalogEntry(
    String scriptCodeId,
    String scriptName,
    String description,
    String activeVersion,
    String latestPublishedVersion,
    boolean published) {}
