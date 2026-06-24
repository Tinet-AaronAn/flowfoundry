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
import com.example.platform.callcampaign.service.CampaignStore;
import com.example.platform.callcampaign.service.DialerService;
import com.example.platform.idempotency.IdempotentActivityExecutor;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class CallCampaignActivitiesImpl implements CallCampaignActivities {

  private static final Logger log = LoggerFactory.getLogger(CallCampaignActivitiesImpl.class);

  private final IdempotentActivityExecutor idempotentExecutor;
  private final CampaignStore campaignStore;
  private final DialerService dialerService;

  public CallCampaignActivitiesImpl(
      IdempotentActivityExecutor idempotentExecutor,
      CampaignStore campaignStore,
      DialerService dialerService) {
    this.idempotentExecutor = idempotentExecutor;
    this.campaignStore = campaignStore;
    this.dialerService = dialerService;
  }

  @Override
  public LoadCampaignResult loadCampaign(String campaignId) {
    return idempotentExecutor.execute(
        "load-campaign",
        Map.of("campaignId", campaignId),
        () -> campaignStore.load(campaignId));
  }

  @Override
  public PrepareRoundResult prepareCallRound(String campaignId, int roundNumber) {
    return idempotentExecutor.execute(
        "prepare-call-round",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> campaignStore.prepareRound(campaignId, roundNumber));
  }

  @Override
  public ExecuteRoundResult executeCallRound(
      String campaignId, int roundNumber, String batchId) {
    return idempotentExecutor.execute(
        "execute-call-round",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> {
          int contactCount = campaignStore.batchSize(batchId);
          if (contactCount == 0) {
            contactCount = 100;
          }
          String taskId =
              dialerService.submitBatch(campaignId, roundNumber, batchId, contactCount);
          return new ExecuteRoundResult(taskId, contactCount);
        });
  }

  @Override
  public RoundCompletionResult waitRoundCompletion(
      String campaignId, int roundNumber, String dialerTaskId) {
    heartbeat("waiting dialer task " + dialerTaskId);
    return idempotentExecutor.execute(
        "wait-round-completion",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> {
          RoundCompletionResult result = dialerService.pollCompletion(dialerTaskId);
          if (result.pendingCount() > 0) {
            heartbeat("still pending");
            throw new RuntimeException("Dialer task not complete yet");
          }
          campaignStore.recordRoundCompletion(campaignId, roundNumber, result);
          log.info(
              "Round complete campaign={} round={} connected={}",
              campaignId,
              roundNumber,
              result.connectedCount());
          return result;
        });
  }

  @Override
  public AggregateRoundResult aggregateRoundResults(String campaignId, int roundNumber) {
    return idempotentExecutor.execute(
        "aggregate-round-results",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> {
          RoundCompletionResult completion =
              campaignStore.lastRoundCompletion(campaignId, roundNumber);
          if (completion == null) {
            completion = new RoundCompletionResult(0, 0, 0, 0);
          }
          return campaignStore.aggregate(campaignId, roundNumber, completion);
        });
  }

  @Override
  public EvaluateNextRoundResult evaluateNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    return idempotentExecutor.execute(
        "evaluate-next-round",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> campaignStore.evaluate(campaignId, roundNumber, remainingContacts, maxRounds));
  }

  @Override
  public SupervisorReviewResult supervisorReview(SupervisorReviewRequest request) {
    return idempotentExecutor.execute(
        "supervisor-review",
        Map.of("campaignId", request.campaignId(), "roundNumber", request.roundNumber()),
        () -> campaignStore.createSupervisorTask(request));
  }

  @Override
  public FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted) {
    return idempotentExecutor.execute(
        "finalize-campaign",
        Map.of("campaignId", campaignId),
        () -> campaignStore.finalizeCampaign(campaignId, totalRoundsExecuted));
  }

  private void heartbeat(String detail) {
    ActivityExecutionContext ctx = Activity.getExecutionContext();
    if (ctx != null) {
      ctx.heartbeat(detail);
    }
  }
}
