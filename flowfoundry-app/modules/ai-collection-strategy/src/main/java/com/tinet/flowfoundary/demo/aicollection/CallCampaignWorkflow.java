package com.tinet.flowfoundary.demo.aicollection;

import com.tinet.flowfoundary.demo.aicollection.model.FinalizeCampaignResult;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/** 本地/Docker 冒烟测试用 Workflow，与 BPMN 流程等价编排 */
@WorkflowInterface
public interface CallCampaignWorkflow {

  @WorkflowMethod
  FinalizeCampaignResult runCampaign(String campaignId);
}
