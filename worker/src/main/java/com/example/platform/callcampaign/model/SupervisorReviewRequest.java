package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record SupervisorReviewRequest(
    String campaignId, int roundNumber, String assigneeRole) implements Serializable {}
