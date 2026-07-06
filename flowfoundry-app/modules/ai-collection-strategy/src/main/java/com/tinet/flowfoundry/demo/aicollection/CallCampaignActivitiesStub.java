package com.tinet.flowfoundry.demo.aicollection;

import com.tinet.flowfoundry.demo.aicollection.model.AggregateRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.EvaluateNextRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.ExecuteRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.FilterSplitResult;
import com.tinet.flowfoundry.demo.aicollection.model.FinalizeCampaignResult;
import com.tinet.flowfoundry.demo.aicollection.model.ImportNumbersResult;
import com.tinet.flowfoundry.demo.aicollection.model.LoadCampaignResult;
import com.tinet.flowfoundry.demo.aicollection.model.NotifyOwnerResult;
import com.tinet.flowfoundry.demo.aicollection.model.PrepareRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.RoundCompletionResult;
import com.tinet.flowfoundry.demo.aicollection.model.StartAiTaggingResult;
import com.tinet.flowfoundry.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundry.demo.aicollection.model.SupervisorReviewResult;
import com.tinet.flowfoundry.demo.aicollection.model.TaggingCompletionResult;
import org.springframework.stereotype.Component;

/**
 * Deterministic Activity stubs for web-modeler debug runs. Long-wait activities return completed
 * state immediately.
 */
@Component
public class CallCampaignActivitiesStub implements CallCampaignActivities {

  @Override
  public ImportNumbersResult importNumbers(String campaignId) {
    return new ImportNumbersResult(100, "stub-import:" + campaignId);
  }

  @Override
  public FilterSplitResult filterAndSplitBatches(String campaignId) {
    return new FilterSplitResult(2, 100);
  }

  @Override
  public NotifyOwnerResult notifyOwnerReport(
      String campaignId, int batchCount, int eligibleContacts) {
    return new NotifyOwnerResult(
        "stub-notify-" + campaignId,
        "stub summary batches=" + batchCount + " eligible=" + eligibleContacts);
  }

  @Override
  public LoadCampaignResult loadCampaign(String campaignId) {
    return new LoadCampaignResult(100, 1, 1);
  }

  @Override
  public PrepareRoundResult prepareCallRound(String campaignId, int roundNumber) {
    return new PrepareRoundResult("stub-batch-" + campaignId + "-r" + roundNumber, 100);
  }

  @Override
  public ExecuteRoundResult executeCallRound(
      String campaignId, int roundNumber, String batchId) {
    return new ExecuteRoundResult("stub-dialer-" + campaignId + "-r" + roundNumber, 100);
  }

  @Override
  public RoundCompletionResult waitRoundCompletion(
      String campaignId, int roundNumber, String dialerTaskId) {
    return new RoundCompletionResult(80, 10, 10, 0);
  }

  @Override
  public AggregateRoundResult aggregateRoundResults(String campaignId, int roundNumber) {
    return new AggregateRoundResult(false, 0, roundNumber + 1);
  }

  @Override
  public StartAiTaggingResult startAiTagging(
      String campaignId, int roundNumber, int recordingCount) {
    int count = recordingCount > 0 ? recordingCount : 80;
    return new StartAiTaggingResult("stub-tagging-" + campaignId + "-r" + roundNumber, count);
  }

  @Override
  public TaggingCompletionResult waitTaggingCompletion(
      String campaignId, int roundNumber, String taggingJobId) {
    return new TaggingCompletionResult(80, 0, 0);
  }

  @Override
  public EvaluateNextRoundResult filterNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    return evaluateNextRound(campaignId, roundNumber, remainingContacts, maxRounds);
  }

  @Override
  public EvaluateNextRoundResult evaluateNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    boolean continueNextRound = remainingContacts > 0 && roundNumber < maxRounds;
    return new EvaluateNextRoundResult(
        continueNextRound, "stub:" + campaignId + ":r" + roundNumber);
  }

  @Override
  public SupervisorReviewResult supervisorReview(SupervisorReviewRequest request) {
    return new SupervisorReviewResult(true, "stub-approved");
  }

  @Override
  public FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted) {
    return new FinalizeCampaignResult(
        "https://stub.local/reports/" + campaignId, "completed-stub");
  }
}
