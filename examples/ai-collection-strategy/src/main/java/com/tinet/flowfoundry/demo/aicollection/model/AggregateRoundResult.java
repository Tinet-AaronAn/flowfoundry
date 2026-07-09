package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record AggregateRoundResult(
    boolean shouldContinue, int remainingContacts, int nextRoundNumber)
    implements Serializable {}
