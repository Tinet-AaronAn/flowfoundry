package com.tinet.flowfoundry.demo.aicollection.model;

import java.io.Serializable;

public record CampaignContext(
    String campaignId,
    int roundNumber,
    int maxRounds,
    int roundIntervalMinutes,
    int totalContacts,
    String batchId,
    String dialerTaskId)
    implements Serializable {

  public CampaignContext withRound(int round) {
    return new CampaignContext(
        campaignId, round, maxRounds, roundIntervalMinutes, totalContacts, batchId, dialerTaskId);
  }

  public CampaignContext withBatch(String newBatchId) {
    return new CampaignContext(
        campaignId,
        roundNumber,
        maxRounds,
        roundIntervalMinutes,
        totalContacts,
        newBatchId,
        dialerTaskId);
  }

  public CampaignContext withDialerTask(String taskId) {
    return new CampaignContext(
        campaignId,
        roundNumber,
        maxRounds,
        roundIntervalMinutes,
        totalContacts,
        batchId,
        taskId);
  }
}
