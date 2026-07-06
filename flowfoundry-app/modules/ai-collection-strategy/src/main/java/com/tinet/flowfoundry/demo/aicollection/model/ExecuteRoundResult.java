package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record ExecuteRoundResult(String dialerTaskId, int submittedCount) implements Serializable {}
