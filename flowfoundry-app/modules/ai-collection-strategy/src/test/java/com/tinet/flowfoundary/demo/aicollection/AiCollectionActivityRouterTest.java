package com.tinet.flowfoundary.demo.aicollection;

import static org.assertj.core.api.Assertions.assertThat;

import com.tinet.flowfoundary.demo.aicollection.model.EvaluateNextRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.ExecuteRoundResult;
import com.tinet.flowfoundary.demo.aicollection.model.ImportNumbersResult;
import com.tinet.flowfoundary.demo.aicollection.model.LoadCampaignResult;
import com.tinet.flowfoundary.demo.aicollection.model.RoundCompletionResult;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewRequest;
import com.tinet.flowfoundary.demo.aicollection.model.SupervisorReviewResult;
import com.tinet.flowfoundary.demo.aicollection.model.TaggingCompletionResult;
import com.tinet.flowfoundary.interpreter.runtime.ActivityExecutionContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AiCollectionActivityRouterTest {

  @Test
  void routesMapInputToStronglyTypedActivity() {
    AiCollectionActivityRouter router = router(new FakeActivities(), new CallCampaignActivitiesStub());

    Object result =
        router.execute(
            "evaluate-next-round",
            Map.of(
                "campaignId", "cmp-1",
                "roundNumber", 2,
                "remainingContacts", 25,
                "maxRounds", 3));

    assertThat(result).isEqualTo(new EvaluateNextRoundResult(true, "cmp-1:2:25:3"));
  }

  @Test
  void routesPositionalArgsForBackwardCompatibility() {
    AiCollectionActivityRouter router = router(new FakeActivities(), new CallCampaignActivitiesStub());

    Object result =
        router.execute(
            "execute-call-round", Map.of("_args", List.of("cmp-1", 1, "batch-1")));

    assertThat(result).isEqualTo(new ExecuteRoundResult("cmp-1:1:batch-1", 100));
  }

  @Test
  void usesStubActivitiesForWebModelerRuns() {
    AiCollectionActivityRouter router = router(new FakeActivities(), new CallCampaignActivitiesStub());

    Object importResult =
        router.execute(
            "import-numbers",
            Map.of("campaignId", "cmp-web", ActivityExecutionContext.CONTEXT_KEY, webContext()));
    Object waitRound =
        router.execute(
            "wait-round-completion",
            Map.of(
                "campaignId",
                "cmp-web",
                "roundNumber",
                1,
                "dialerTaskId",
                "dialer-1",
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));
    Object waitTagging =
        router.execute(
            "wait-tagging-completion",
            Map.of(
                "campaignId",
                "cmp-web",
                "roundNumber",
                1,
                "taggingJobId",
                "tag-1",
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));

    assertThat(importResult)
        .isEqualTo(new ImportNumbersResult(100, "stub-import:cmp-web"));
    assertThat(waitRound).isEqualTo(new RoundCompletionResult(80, 10, 10, 0));
    assertThat(waitTagging).isEqualTo(new TaggingCompletionResult(80, 0, 0));
  }

  @Test
  void stubSupervisorReviewApprovesImmediately() {
    AiCollectionActivityRouter router = router(new FakeActivities(), new CallCampaignActivitiesStub());

    Object result =
        router.execute(
            "supervisor-review",
            Map.of(
                "campaignId",
                "cmp-web",
                "roundNumber",
                1,
                ActivityExecutionContext.CONTEXT_KEY,
                webContext()));

    assertThat(result).isEqualTo(new SupervisorReviewResult(true, "stub-approved"));
  }

  private static AiCollectionActivityRouter router(
      CallCampaignActivities real, CallCampaignActivities stub) {
    return AiCollectionActivityRouter.forTests(real, stub);
  }

  private static Map<String, Object> webContext() {
    return Map.of("runSource", "web-modeler", "businessKey", "bk-1", "workflowId", "wf-1");
  }

  private static class FakeActivities implements CallCampaignActivities {

    @Override
    public ImportNumbersResult importNumbers(String campaignId) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.FilterSplitResult filterAndSplitBatches(
        String campaignId) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.NotifyOwnerResult notifyOwnerReport(
        String campaignId, int batchCount, int eligibleContacts) {
      return null;
    }

    @Override
    public LoadCampaignResult loadCampaign(String campaignId) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.PrepareRoundResult prepareCallRound(
        String campaignId, int roundNumber) {
      return null;
    }

    @Override
    public ExecuteRoundResult executeCallRound(
        String campaignId, int roundNumber, String batchId) {
      return new ExecuteRoundResult(campaignId + ":" + roundNumber + ":" + batchId, 100);
    }

    @Override
    public RoundCompletionResult waitRoundCompletion(
        String campaignId, int roundNumber, String dialerTaskId) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.AggregateRoundResult aggregateRoundResults(
        String campaignId, int roundNumber) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.StartAiTaggingResult startAiTagging(
        String campaignId, int roundNumber, int recordingCount) {
      return null;
    }

    @Override
    public TaggingCompletionResult waitTaggingCompletion(
        String campaignId, int roundNumber, String taggingJobId) {
      return null;
    }

    @Override
    public EvaluateNextRoundResult filterNextRound(
        String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
      return evaluateNextRound(campaignId, roundNumber, remainingContacts, maxRounds);
    }

    @Override
    public EvaluateNextRoundResult evaluateNextRound(
        String campaignId, int roundNumber, int remainingContacts, int maxRounds) {
      return new EvaluateNextRoundResult(
          remainingContacts > 0 && roundNumber < maxRounds,
          campaignId + ":" + roundNumber + ":" + remainingContacts + ":" + maxRounds);
    }

    @Override
    public SupervisorReviewResult supervisorReview(SupervisorReviewRequest request) {
      return null;
    }

    @Override
    public com.tinet.flowfoundary.demo.aicollection.model.FinalizeCampaignResult finalizeCampaign(
        String campaignId, int totalRoundsExecuted) {
      return null;
    }
  }
}
