package com.tinet.flowfoundry.demo.aicollection.service;

import com.tinet.flowfoundry.demo.aicollection.model.TaggingCompletionResult;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 录音 AI 打标 Adapter 桩 — 生产环境替换为真实 AI 服务客户端。
 * wait-tagging-completion Activity 通过轮询/重试等待终态。
 */
@Service
public class AiTaggingService {

  private static final Logger log = LoggerFactory.getLogger(AiTaggingService.class);

  private final Map<String, TaggingCompletionResult> jobResults = new ConcurrentHashMap<>();

  public String submitTaggingJob(String campaignId, int roundNumber, int recordingCount) {
    String jobId = "tagging-" + campaignId + "-r" + roundNumber + "-" + UUID.randomUUID();
    log.info(
        "Submitted AI tagging campaign={} round={} recordings={} jobId={}",
        campaignId,
        roundNumber,
        recordingCount,
        jobId);
    int tagged = Math.max(0, recordingCount - 2);
    int failed = Math.min(2, recordingCount);
    jobResults.put(jobId, new TaggingCompletionResult(tagged, failed, 0));
    return jobId;
  }

  public TaggingCompletionResult pollCompletion(String taggingJobId) {
    TaggingCompletionResult result = jobResults.get(taggingJobId);
    if (result == null) {
      return new TaggingCompletionResult(0, 0, 1);
    }
    return result;
  }

  public int defaultRecordingCount(int connectedCount) {
    return connectedCount > 0 ? connectedCount : 50;
  }
}
