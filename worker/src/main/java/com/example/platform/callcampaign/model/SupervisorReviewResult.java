package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record SupervisorReviewResult(boolean approved, String comment) implements Serializable {}
