package com.tinet.flowfoundary.demo.aicollection.model;

import java.io.Serializable;

public record AggregateRoundResult(
    boolean shouldContinue, int remainingContacts, int nextRoundNumber)
    implements Serializable {}
