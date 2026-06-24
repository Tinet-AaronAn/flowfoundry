package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record EvaluateNextRoundResult(boolean continueNextRound, String reason)
    implements Serializable {}
