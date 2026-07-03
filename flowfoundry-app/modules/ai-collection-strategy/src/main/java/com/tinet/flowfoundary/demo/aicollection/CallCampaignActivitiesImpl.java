package com.tinet.flowfoundary.demo.aicollection;

import com.tinet.flowfoundary.demo.aicollection.model.AggregateRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.EvaluateNextRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.ExecuteRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.FilterSplitResult;
import com.tinet.flowfoundary.demo.aicollection.model.FinalizeCampaignResult;
import com.tinet.flowfoundary.demo.aicollection.model.ImportNumbersResult;
import com.tinet.flowfoundary.demo.aicollection.model.LoadCampaignResult;
import com.tinet.flowfoundary.demo.aicollection.model.NotifyOwnerResult;
import com.tinet.flowfoundary.demo.aicollection.model.PrepareRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.RoundCompletionResult;
import com.tinet.flowfoundary.demo.aicollection.model.StartAiTaggingResult;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewResult;
import com.tinet.flowfoundary.demo.aicollection.model.TaggingCompletionResult;
import com.tinet.flowfoundary.demo.aicollection.service.AiTaggingService;
import com.tinet.flowfoundary.demo.aicollection.service.CampaignStore;
import com.tinet.flowfoundary.demo.aicollection.service.DialerService;
import com.tinet.flowfoundary.idempotency.IdempotentActivityExecutor;
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
  private final AiTaggingService aiTaggingService;

  public CallCampaignActivitiesImpl(
      IdempotentActivityExecutor idempotentExecutor,
      CampaignStore campaignStore,
      DialerService dialerService,
      AiTaggingService aiTaggingService) {
    this.idempotentExecutor = idempotentExecutor;
    this.campaignStore = campaignStore;
    this.dialerService = dialerService;
    this.aiTaggingService = aiTaggingService;
  }

  @Override
  public ImportNumbersResult importNumbers(String campaignId) {
    return idempotentExecutor.execute(
        "import-numbers",
        Map.of("campaignId", campaignId),
        () -> campaignStore.importNumbers(campaignId));
  }

  @Override
  public FilterSplitResult filterAndSplitBatches(String campaignId) {
    return idempotentExecutor.execute(
        "filter-and-split-batches",
        Map.of("campaignId", campaignId),
        () -> campaignStore.filterAndSplitBatches(campaignId));
  }

  @Override
  public NotifyOwnerResult notifyOwnerReport(
      String campaignId, int batchCount, int eligibleContacts) {
    return idempotentExecutor.execute(
        "notify-owner-report",
        Map.of("campaignId", campaignId),
        () -> campaignStore.notifyOwnerReport(campaignId, batchCount, eligibleContacts));
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
  public EvaluateNextRoundResult filterNextRound(
      String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
    return idempotentExecutor.execute(
        "filter-next-round",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () ->
            campaignStore.filterNextRound(
                campaignId, roundNumber, remainingContacts, maxRounds));
  }

  @Override
  public StartAiTaggingResult startAiTagging(
      String campaignId, int roundNumber, int recordingCount) {
    return idempotentExecutor.execute(
        "start-ai-tagging",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> {
          int count = recordingCount > 0 ? recordingCount : aiTaggingService.defaultRecordingCount(0);
          RoundCompletionResult completion =
              campaignStore.lastRoundCompletion(campaignId, roundNumber);
          if (completion != null && completion.connectedCount() > 0) {
            count = completion.connectedCount();
          }
          String jobId = aiTaggingService.submitTaggingJob(campaignId, roundNumber, count);
          return new StartAiTaggingResult(jobId, count);
        });
  }

  @Override
  public TaggingCompletionResult waitTaggingCompletion(
      String campaignId, int roundNumber, String taggingJobId) {
    heartbeat("waiting AI tagging job " + taggingJobId);
    return idempotentExecutor.execute(
        "wait-tagging-completion",
        Map.of("campaignId", campaignId, "roundNumber", roundNumber),
        () -> {
          TaggingCompletionResult result = aiTaggingService.pollCompletion(taggingJobId);
          if (result.pendingCount() > 0) {
            heartbeat("tagging still pending");
            throw new RuntimeException("AI tagging job not complete yet");
          }
          log.info(
              "Tagging complete campaign={} round={} tagged={}",
              campaignId,
              roundNumber,
              result.taggedCount());
          return result;
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
