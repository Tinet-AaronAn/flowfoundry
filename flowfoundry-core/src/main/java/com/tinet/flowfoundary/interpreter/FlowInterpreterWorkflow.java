package com.tinet.flowfoundary.interpreter;

import com.tinet.flowfoundary.interpreter.model.ExecutionPlan;
import com.tinet.flowfoundary.interpreter.model.HumanTaskCompletion;
import com.tinet.flowfoundary.interpreter.model.HumanTaskNodeState;
import com.tinet.flowfoundary.interpreter.model.InterpreterState;
import java.util.List;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

@WorkflowInterface
public interface FlowInterpreterWorkflow {

  @WorkflowMethod
  InterpreterState run(
      ExecutionPlan plan, String businessKey, Map<String, Object> input, String runSource);

  @QueryMethod
  InterpreterState getState();

  @QueryMethod
  List<HumanTaskNodeState> getHumanTasks();

  @SignalMethod
  void completeHumanTask(HumanTaskCompletion completion);
}
