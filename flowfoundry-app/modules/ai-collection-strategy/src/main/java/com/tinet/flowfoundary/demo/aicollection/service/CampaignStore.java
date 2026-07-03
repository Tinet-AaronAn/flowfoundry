package com.tinet.flowfoundary.demo.aicollection.service;

import com.tinet.flowfoundary.demo.aicollection.model.AggregateRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.CampaignContext;
import com.tinet.flowfoundary.demo.aicollection.model.EvaluateNextRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.FilterSplitResult;
import com.tinet.flowfoundary.demo.aicollection.model.FinalizeCampaignResult;
import com.tinet.flowfoundary.demo.aicollection.model.ImportNumbersResult;
import com.tinet.flowfoundary.demo.aicollection.model.LoadCampaignResult;
import com.tinet.flowfoundary.demo.aicollection.model.NotifyOwnerResult;
import com.tinet.flowfoundary.demo.aicollection.model.PrepareRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.RoundCompletionResult;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewResult;
import java.util.Map;
import java.util.Set;
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
  private final Set<String> aggregatedRounds = ConcurrentHashMap.newKeySet();

  public ImportNumbersResult importNumbers(String campaignId) {
    LoadCampaignResult loaded = load(campaignId);
    return new ImportNumbersResult(loaded.totalContacts(), campaignId + "-import");
  }

  public FilterSplitResult filterAndSplitBatches(String campaignId) {
    int total = remainingByCampaign.getOrDefault(campaignId, 500);
    int batchCount = Math.max(1, (int) Math.ceil(total / 200.0));
    log.info("Filtered and split campaign={} batches={} eligible={}", campaignId, batchCount, total);
    return new FilterSplitResult(batchCount, total);
  }

  public NotifyOwnerResult notifyOwnerReport(String campaignId, int batchCount, int eligibleContacts) {
    String summary =
        batchCount + " batches, " + eligibleContacts + " eligible contacts for campaign " + campaignId;
    log.info("Owner report sent campaign={} summary={}", campaignId, summary);
    return new NotifyOwnerResult("notify-" + campaignId, summary);
  }

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

  public EvaluateNextRoundResult filterNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    RoundCompletionResult completion = lastRoundCompletion(campaignId, roundNumber);
    String aggregateKey = campaignId + ":r" + roundNumber;
    if (completion != null && aggregatedRounds.add(aggregateKey)) {
      AggregateRoundResult aggregated = aggregate(campaignId, roundNumber, completion);
      remainingContacts = aggregated.remainingContacts();
    } else {
      remainingContacts = remainingByCampaign.getOrDefault(campaignId, remainingContacts);
    }
    CampaignContext ctx = campaigns.get(campaignId);
    if (ctx != null) {
      maxRounds = ctx.maxRounds();
    }
    log.info(
        "Filtered next-round candidates campaign={} round={} remaining={}",
        campaignId,
        roundNumber,
        remainingContacts);
    return evaluate(campaignId, roundNumber, remainingContacts, maxRounds);
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
    // FlowFoundry Human Task 在 Inbox 完成；此处桩默认自动批准
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
