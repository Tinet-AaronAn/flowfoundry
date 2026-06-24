package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record ExecuteRoundResult(String dialerTaskId, int submittedCount) implements Serializable {}
