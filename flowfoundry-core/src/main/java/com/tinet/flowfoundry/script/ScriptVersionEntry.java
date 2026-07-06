package com.tinet.flowfoundry.script;

public record ScriptVersionEntry(
    String version, boolean published, boolean active, String label) {}
