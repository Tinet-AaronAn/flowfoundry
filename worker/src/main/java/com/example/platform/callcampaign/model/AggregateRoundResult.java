package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record AggregateRoundResult(
    boolean shouldContinue, int remainingContacts, int nextRoundNumber)
    implements Serializable {}
