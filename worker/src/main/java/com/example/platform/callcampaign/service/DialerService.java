package com.example.platform.callcampaign.service;

import com.example.platform.callcampaign.model.RoundCompletionResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 外呼平台 Adapter 桩实现 — 生产环境替换为真实 HTTP/gRPC 客户端。
 * 演示：提交后异步完成，wait-round-completion Activity 轮询终态。
 */
@Service
public class DialerService {

  private static final Logger log = LoggerFactory.getLogger(DialerService.class);

  private final Map<String, RoundCompletionResult> taskResults = new ConcurrentHashMap<>();

  public String submitBatch(String campaignId, int roundNumber, String batchId, int contactCount) {
    String taskId = "dialer-" + campaignId + "-r" + roundNumber + "-" + UUID.randomUUID();
    log.info(
        "Submitted dialer batch campaign={} round={} batch={} contacts={} taskId={}",
        campaignId,
        roundNumber,
        batchId,
        contactCount,
        taskId);

    int connected = (int) (contactCount * 0.35);
    int noAnswer = (int) (contactCount * 0.45);
    int failed = contactCount - connected - noAnswer;
    taskResults.put(
        taskId, new RoundCompletionResult(connected, noAnswer, failed, 0));
    return taskId;
  }

  public RoundCompletionResult pollCompletion(String dialerTaskId) {
    RoundCompletionResult result = taskResults.get(dialerTaskId);
    if (result == null) {
      return new RoundCompletionResult(0, 0, 0, 1);
    }
    return result;
  }
}
