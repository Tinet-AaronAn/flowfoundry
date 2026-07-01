package com.example.platform.callcampaign;

import com.example.platform.callcampaign.model.AggregateRoundResult;
import com.example.platform.callcampaign.model.EvaluateNextRoundResult;
import com.example.platform.callcampaign.model.ExecuteRoundResult;
import com.example.platform.callcampaign.model.FinalizeCampaignResult;
import com.example.platform.callcampaign.model.LoadCampaignResult;
import com.example.platform.callcampaign.model.PrepareRoundResult;
import com.example.platform.callcampaign.model.RoundCompletionResult;
import com.example.platform.callcampaign.model.SupervisorReviewRequest;
import com.example.platform.callcampaign.model.SupervisorReviewResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * FlowFoundry BPMN Service Task 通过 flowfoundry:activityType 映射到此接口方法。
 * 方法名与 registry/activities-registry.yaml 中的 id 对应。
 */
@ActivityInterface
public interface CallCampaignActivities {

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

  @ActivityMethod(name = "evaluate-next-round")
  EvaluateNextRoundResult evaluateNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds);

  @ActivityMethod(name = "supervisor-review")
  SupervisorReviewResult supervisorReview(SupervisorReviewRequest request);

  @ActivityMethod(name = "finalize-campaign")
  FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted);
}
