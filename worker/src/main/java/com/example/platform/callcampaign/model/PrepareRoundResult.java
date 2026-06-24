package com.example.platform.callcampaign.model;

import java.io.Serializable;

public record PrepareRoundResult(String batchId, int contactCount) implements Serializable {}
