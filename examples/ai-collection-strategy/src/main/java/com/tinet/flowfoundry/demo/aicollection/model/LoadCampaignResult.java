package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record LoadCampaignResult(
    int totalContacts, int maxRounds, int roundIntervalMinutes) implements Serializable {}
