package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record SupervisorReviewResult(boolean approved, String comment) implements Serializable {}
