package com.tinet.flowfoundry.demo.aicollection;

import com.tinet.flowfoundry.demo.aicollection.model.AggregateRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.EvaluateNextRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.ExecuteRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.FinalizeCampaignResult;
import com.tinet.flowfoundry.demo.aicollection.model.LoadCampaignResult;
import com.tinet.flowfoundry.demo.aicollection.model.PrepareRoundResult;
import com.tinet.flowfoundry.demo.aicollection.model.RoundCompletionResult;
import com.tinet.flowfoundry.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundry.demo.aicollection.model.SupervisorReviewResult;
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
              .setTaskQueue("ai-collection-strategy")
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
