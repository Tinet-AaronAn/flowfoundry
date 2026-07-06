package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record FinalizeCampaignResult(String reportUrl, String finalStatus)
    implements Serializable {}
