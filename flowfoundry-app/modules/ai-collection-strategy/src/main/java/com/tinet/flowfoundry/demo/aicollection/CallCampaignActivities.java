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
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * FlowFoundry BPMN Service Task 通过 flowfoundry:activityType 映射到此接口方法。
 * 方法名与 registry/activities-registry.yaml 中的 id 对应。
 */
@ActivityInterface
public interface CallCampaignActivities {

  @ActivityMethod(name = "import-numbers")
  ImportNumbersResult importNumbers(String campaignId);

  @ActivityMethod(name = "filter-and-split-batches")
  FilterSplitResult filterAndSplitBatches(String campaignId);

  @ActivityMethod(name = "notify-owner-report")
  NotifyOwnerResult notifyOwnerReport(String campaignId, int batchCount, int eligibleContacts);

  @ActivityMethod(name = "load-campaign")
  LoadCampaignResult loadCampaign(String campaignId);

  @ActivityMethod(name = "prepare-call-round")
  PrepareRoundResult prepareCallRound(String campaignId, int roundNumber);

  @ActivityMethod(name = "execute-call-round")
  ExecuteRoundResult executeCallRound(String campaignId, int roundNumber, String batchId);

  @ActivityMethod(name = "wait-round-completion")
  RoundCompletionResult waitRoundCompletion(
      String campaignId, int roundNumber, String dialerTaskId);

  @ActivityMethod(name = "aggregate-round-results")
  AggregateRoundResult aggregateRoundResults(String campaignId, int roundNumber);

  @ActivityMethod(name = "start-ai-tagging")
  StartAiTaggingResult startAiTagging(String campaignId, int roundNumber, int recordingCount);

  @ActivityMethod(name = "wait-tagging-completion")
  TaggingCompletionResult waitTaggingCompletion(
      String campaignId, int roundNumber, String taggingJobId);

  @ActivityMethod(name = "filter-next-round")
  EvaluateNextRoundResult filterNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds);

  @ActivityMethod(name = "evaluate-next-round")
  EvaluateNextRoundResult evaluateNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds);

  @ActivityMethod(name = "supervisor-review")
  SupervisorReviewResult supervisorReview(SupervisorReviewRequest request);

  @ActivityMethod(name = "finalize-campaign")
  FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted);
}
