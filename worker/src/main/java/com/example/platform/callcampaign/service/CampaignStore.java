package com.example.platform.callcampaign.service;

import com.example.platform.callcampaign.model.AggregateRoundResult;
import com.example.platform.callcampaign.model.CampaignContext;
import com.example.platform.callcampaign.model.EvaluateNextRoundResult;
import com.example.platform.callcampaign.model.FinalizeCampaignResult;
import com.example.platform.callcampaign.model.LoadCampaignResult;
import com.example.platform.callcampaign.model.PrepareRoundResult;
import com.example.platform.callcampaign.model.RoundCompletionResult;
import com.example.platform.callcampaign.model.SupervisorReviewRequest;
import com.example.platform.callcampaign.model.SupervisorReviewResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CampaignStore {

  private static final Logger log = LoggerFactory.getLogger(CampaignStore.class);

  private final Map<String, CampaignContext> campaigns = new ConcurrentHashMap<>();
  private final Map<String, Integer> remainingByCampaign = new ConcurrentHashMap<>();
  private final Map<String, Integer> batchSizes = new ConcurrentHashMap<>();
  private final Map<String, RoundCompletionResult> lastRoundCompletion = new ConcurrentHashMap<>();

  public LoadCampaignResult load(String campaignId) {
    int total = 500;
    int maxRounds = intEnv("DEV_MAX_ROUNDS", 3);
    int interval = intEnv("DEV_ROUND_INTERVAL_MINUTES", 30);
    campaigns.put(
        campaignId,
        new CampaignContext(campaignId, 1, maxRounds, interval, total, null, null));
    remainingByCampaign.put(campaignId, total);
    return new LoadCampaignResult(total, maxRounds, interval);
  }

  public PrepareRoundResult prepareRound(String campaignId, int roundNumber) {
    int remaining = remainingByCampaign.getOrDefault(campaignId, 0);
    int batchSize = Math.min(200, remaining);
    String batchId = campaignId + "-batch-r" + roundNumber + "-" + UUID.randomUUID();
    batchSizes.put(batchId, batchSize);
    log.info(
        "Prepared round campaign={} round={} batch={} size={}",
        campaignId,
        roundNumber,
        batchId,
        batchSize);
    return new PrepareRoundResult(batchId, batchSize);
  }

  public int batchSize(String batchId) {
    return batchSizes.getOrDefault(batchId, 0);
  }

  public void recordRoundCompletion(String campaignId, int roundNumber, RoundCompletionResult result) {
    lastRoundCompletion.put(campaignId + ":r" + roundNumber, result);
  }

  public RoundCompletionResult lastRoundCompletion(String campaignId, int roundNumber) {
    return lastRoundCompletion.get(campaignId + ":r" + roundNumber);
  }

  public AggregateRoundResult aggregate(
      String campaignId, int roundNumber, RoundCompletionResult completion) {
    int remaining = remainingByCampaign.getOrDefault(campaignId, 0);
    int reached = completion.connectedCount();
    int newRemaining = Math.max(0, remaining - reached);
    remainingByCampaign.put(campaignId, newRemaining);

    CampaignContext ctx = campaigns.get(campaignId);
    int maxRounds = ctx != null ? ctx.maxRounds() : 3;
    boolean shouldContinue = newRemaining > 0 && roundNumber < maxRounds;

    log.info(
        "Aggregated campaign={} round={} reached={} remaining={} continue={}",
        campaignId,
        roundNumber,
        reached,
        newRemaining,
        shouldContinue);

    return new AggregateRoundResult(shouldContinue, newRemaining, roundNumber + 1);
  }

  public EvaluateNextRoundResult evaluate(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    if (remainingContacts <= 0) {
      return new EvaluateNextRoundResult(false, "no remaining contacts");
    }
    if (roundNumber >= maxRounds) {
      return new EvaluateNextRoundResult(false, "max rounds reached");
    }
    return new EvaluateNextRoundResult(true, "pending contacts remain");
  }

  public FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted) {
    remainingByCampaign.remove(campaignId);
    campaigns.remove(campaignId);
    String reportUrl = "https://reports.internal.example.com/campaigns/" + campaignId;
    log.info("Finalized campaign={} rounds={}", campaignId, totalRoundsExecuted);
    return new FinalizeCampaignResult(reportUrl, "COMPLETED");
  }

  public SupervisorReviewResult createSupervisorTask(SupervisorReviewRequest request) {
    log.info(
        "Created supervisor review task campaign={} round={} role={}",
        request.campaignId(),
        request.roundNumber(),
        request.assigneeRole());
    // QuantumBPM User Task 在 Inbox 完成；此处桩默认自动批准
    return new SupervisorReviewResult(true, "auto-approved-stub");
  }

  private static int intEnv(String key, int defaultValue) {
    String raw = System.getenv(key);
    if (raw == null || raw.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
