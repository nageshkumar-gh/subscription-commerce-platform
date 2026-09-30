package com.example.orchestration.controller;

import com.example.orchestration.workflow.OrderLifecycleWorkflow;
import com.example.orchestration.workflow.OrderWorkflowRequest;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Starts an order's lifecycle workflow exactly once. */
@Service
public class OrderWorkflows {
    private final WorkflowClient client;
    private final OrderCancellation cancellation;

    public OrderWorkflows(WorkflowClient client, OrderCancellation cancellation) {
        this.client = client;
        this.cancellation = cancellation;
    }

    /** Returns STARTED, or the current state if the order's workflow already ran (a retried checkout is harmless). */
    public String start(OrderWorkflowRequest request) {
        if (cancellation.isCancelled(request.orderId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Order is cancelled");
        // An order runs through its lifecycle once: REJECT_DUPLICATE stops a finished order (completed, failed or cancelled) from being restarted.
        OrderLifecycleWorkflow workflow = client.newWorkflowStub(OrderLifecycleWorkflow.class, WorkflowOptions.newBuilder()
            .setWorkflowId("order-" + request.orderId()).setTaskQueue("order-lifecycle")
            .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE).build());
        try {
            WorkflowClient.start(workflow::run, request);
            return "STARTED";
        } catch (WorkflowExecutionAlreadyStarted alreadyStarted) {
            return cancellation.state(request.orderId());
        }
    }
}
