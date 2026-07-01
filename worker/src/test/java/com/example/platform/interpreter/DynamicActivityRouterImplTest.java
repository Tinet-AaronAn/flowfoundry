package com.example.platform.interpreter;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.platform.callcampaign.CallCampaignActivities;
import com.example.platform.callcampaign.model.AggregateRoundResult;
import com.example.platform.callcampaign.model.EvaluateNextRoundResult;
import com.example.platform.callcampaign.model.ExecuteRoundResult;
import com.example.platform.callcampaign.model.FinalizeCampaignResult;
import com.example.platform.callcampaign.model.LoadCampaignResult;
import com.example.platform.callcampaign.model.PrepareRoundResult;
import com.example.platform.callcampaign.model.RoundCompletionResult;
import com.example.platform.callcampaign.model.SupervisorReviewRequest;
import com.example.platform.callcampaign.model.SupervisorReviewResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DynamicActivityRouterImplTest {

  @Test
  void routesMapInputToStronglyTypedActivity() {
    DynamicActivityRouterImpl router =
        new DynamicActivityRouterImpl(new FakeActivities(), new ObjectMapper(), "");

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
    DynamicActivityRouterImpl router =
        new DynamicActivityRouterImpl(new FakeActivities(), new ObjectMapper(), "");

    Object result =
        router.execute(
            "execute-call-round", Map.of("_args", List.of("cmp-1", 1, "batch-1")));

    assertThat(result).isEqualTo(new ExecuteRoundResult("cmp-1:1:batch-1", 100));
  }

  @Test
  void supportsDmnDecisionFallbackWhenExternalServiceIsNotConfigured() {
    DynamicActivityRouterImpl router =
        new DynamicActivityRouterImpl(new FakeActivities(), new ObjectMapper(), "");

    Object result =
        router.execute(
            "dmn-decision",
            Map.of("_config", Map.of("decisionRef", "risk-routing", "decisionVersion", "1.0.0")));

    assertThat(result)
        .isEqualTo(
            Map.of(
                "decisionRef",
                "risk-routing",
                "decisionVersion",
                "1.0.0",
                "matched",
                true,
                "output",
                Map.of(
                    "_config",
                    Map.of("decisionRef", "risk-routing", "decisionVersion", "1.0.0"))));
  }

  private static class FakeActivities implements CallCampaignActivities {

    @Override
    public LoadCampaignResult loadCampaign(String campaignId) {
      return null;
    }

    @Override
    public PrepareRoundResult prepareCallRound(String campaignId, int roundNumber) {
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
    public AggregateRoundResult aggregateRoundResults(String campaignId, int roundNumber) {
      return null;
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
    public FinalizeCampaignResult finalizeCampaign(String campaignId, int totalRoundsExecuted) {
      return null;
    }
  }
}
