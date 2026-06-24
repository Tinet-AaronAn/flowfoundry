package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record RoundCompletionResult(
    int connectedCount, int noAnswerCount, int failedCount, int pendingCount)
    implements Serializable {}
