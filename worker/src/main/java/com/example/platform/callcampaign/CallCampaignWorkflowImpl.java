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
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;
import java.time.Duration;

public class CallCampaignWorkflowImpl implements CallCampaignWorkflow {

  private final CallCampaignActivities activities =
      Workflow.newActivityStub(
          CallCampaignActivities.class,
          ActivityOptions.newBuilder()
              .setStartToCloseTimeout(Duration.ofMinutes(10))
              .setTaskQueue("call-campaign")
              .setRetryOptions(
                  RetryOptions.newBuilder().setMaximumAttempts(5).build())
              .build());

  @Override
  public FinalizeCampaignResult runCampaign(String campaignId) {
    LoadCampaignResult loaded = activities.loadCampaign(campaignId);
    int round = 1;
    int totalRoundsExecuted = 0;

    while (true) {
      PrepareRoundResult prepared = activities.prepareCallRound(campaignId, round);
      ExecuteRoundResult executed =
          activities.executeCallRound(campaignId, round, prepared.batchId());
      RoundCompletionResult completion =
          activities.waitRoundCompletion(campaignId, round, executed.dialerTaskId());
      AggregateRoundResult aggregated =
          activities.aggregateRoundResults(campaignId, round);
      totalRoundsExecuted = round;

      if (aggregated.remainingContacts() > 100) {
        SupervisorReviewResult review =
            activities.supervisorReview(
                new SupervisorReviewRequest(campaignId, round, "supervisor"));
        if (!review.approved()) {
          break;
        }
      }

      EvaluateNextRoundResult eval =
          activities.evaluateNextRound(
              campaignId,
              round,
              aggregated.remainingContacts(),
              loaded.maxRounds());

      if (!eval.continueNextRound()) {
        break;
      }

      Workflow.sleep(Duration.ofMinutes(Math.max(1, loaded.roundIntervalMinutes())));
      round = aggregated.nextRoundNumber();
    }

    return activities.finalizeCampaign(campaignId, totalRoundsExecuted);
  }
}
