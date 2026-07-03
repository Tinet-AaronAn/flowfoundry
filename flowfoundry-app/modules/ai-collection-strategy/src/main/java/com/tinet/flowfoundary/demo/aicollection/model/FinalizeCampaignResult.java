package com.tinet.flowfoundary.demo.aicollection.model;

import java.io.Serializable;

public record FinalizeCampaignResult(String reportUrl, String finalStatus)
    implements Serializable {}
