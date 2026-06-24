package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record FinalizeCampaignResult(String reportUrl, String finalStatus)
    implements Serializable {}
