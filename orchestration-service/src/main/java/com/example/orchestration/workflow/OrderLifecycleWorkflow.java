package com.example.orchestration.workflow;import io.temporal.workflow.*;@WorkflowInterface public interface OrderLifecycleWorkflow{@WorkflowMethod String run(OrderWorkflowRequest request);@QueryMethod String status();
/** Operator cancellation: stops the saga at its next check and compensates the steps already taken. */
@SignalMethod void cancel(String reason);}
