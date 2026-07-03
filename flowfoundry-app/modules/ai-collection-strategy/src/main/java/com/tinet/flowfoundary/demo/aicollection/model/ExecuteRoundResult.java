package com.tinet.flowfoundary.demo.aicollection.model;

import java.io.Serializable;

public record ExecuteRoundResult(String dialerTaskId, int submittedCount) implements Serializable {}
