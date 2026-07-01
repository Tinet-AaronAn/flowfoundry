package com.example.platform.interpreter;

import com.example.platform.interpreter.model.ExecutionPlan;
import com.example.platform.interpreter.model.HumanTaskCompletion;
import com.example.platform.interpreter.model.InterpreterState;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import java.util.Map;

@WorkflowInterface
public interface FlowInterpreterWorkflow {

  @WorkflowMethod
  InterpreterState run(ExecutionPlan plan, String businessKey, Map<String, Object> input);

  @QueryMethod
  InterpreterState getState();

  @SignalMethod
  void completeHumanTask(HumanTaskCompletion completion);
}
