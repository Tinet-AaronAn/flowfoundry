package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record EvaluateNextRoundResult(boolean continueNextRound, String reason)
    implements Serializable {}
