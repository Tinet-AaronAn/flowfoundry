package com.tinet.flowfoundary.demo.aicollection.model;

import java.io.Serializable;

public record PrepareRoundResult(String batchId, int contactCount) implements Serializable {}
