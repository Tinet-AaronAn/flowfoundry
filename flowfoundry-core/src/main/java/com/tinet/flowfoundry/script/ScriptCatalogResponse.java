package com.tinet.flowfoundry.script;

import java.util.List;

public record ScriptCatalogResponse(
    String source, List<ScriptCatalogEntry> scripts, List<ScriptVersionEntry> versions) {}
