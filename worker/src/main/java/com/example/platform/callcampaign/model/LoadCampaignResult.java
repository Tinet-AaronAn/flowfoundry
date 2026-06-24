package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record LoadCampaignResult(
    int totalContacts, int maxRounds, int roundIntervalMinutes) implements Serializable {}
