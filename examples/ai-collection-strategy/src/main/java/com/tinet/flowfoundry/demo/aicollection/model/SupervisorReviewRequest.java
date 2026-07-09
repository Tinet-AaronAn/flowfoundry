package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record SupervisorReviewRequest(
    String campaignId, int roundNumber, String assigneeRole) implements Serializable {}
