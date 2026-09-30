package com.example.orchestration.controller;

import com.example.orchestration.workflow.OrderActivities;
import com.example.orchestration.workflow.OrderLifecycleWorkflow;
import com.example.orchestration.workflow.OrderWorkflowRequest;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.failure.ApplicationFailure;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Cancels any order that has not completed. A running workflow is signalled so it compensates itself; an order with no
 * workflow (it never started) or a failed one is compensated here directly, since there is no workflow left to do it.
 */
@Service
public class OrderCancellation {
    private static final Set<String> NOT_CANCELLABLE = Set.of("COMPLETED", "CANCELLING", "CANCELLED");
    private final WorkflowClient client;
    private final OrderActivities activities;
    private final RestClient http;
    private final String orders;

    public OrderCancellation(WorkflowClient client, OrderActivities activities, RestClient.Builder http, @Value("${services.order-url}") String orders) {
        this.client = client;
        this.activities = activities;
        this.http = http.build();
        this.orders = orders;
    }

    /** The workflow's state, NOT_STARTED when there is none. Falls back to Temporal's execution status if the query cannot run. */
    public String state(String orderId) {
        String id = "order-" + orderId;
        try {
            return client.newWorkflowStub(OrderLifecycleWorkflow.class, id).status();
        } catch (WorkflowNotFoundException notFound) {
            return "NOT_STARTED";
        } catch (RuntimeException queryFailed) {
            // e.g. history recorded by an older version of the workflow that no longer replays
            try {
                var stub = client.newUntypedWorkflowStub(id);
                WorkflowExecutionStatus status = stub.describe().getWorkflowExecutionInfo().getStatus();
                return switch (status) {
                    // The workflow returns its final state, so a cancelled order also completes normally: use its result.
                    case WORKFLOW_EXECUTION_STATUS_COMPLETED -> stub.getResult(String.class);
                    case WORKFLOW_EXECUTION_STATUS_RUNNING, WORKFLOW_EXECUTION_STATUS_CONTINUED_AS_NEW -> "RUNNING";
                    case WORKFLOW_EXECUTION_STATUS_CANCELED -> "CANCELLED";
                    default -> "FAILED";
                };
            } catch (WorkflowNotFoundException notFound) {
                return "NOT_STARTED";
            }
        }
    }

    public String cancel(String orderId, String reason) {
        Map<?, ?> order = order(orderId);
        if ("CANCELLED".equals(order.get("status"))) throw conflict("Order is already cancelled");
        String state = state(orderId);
        if (NOT_CANCELLABLE.contains(state)) {
            throw conflict("Order is " + state + " and can no longer be cancelled" + ("COMPLETED".equals(state) ? "; cancel its subscription instead" : ""));
        }
        String trimmed = reason.trim();
        if (!"NOT_STARTED".equals(state) && !"FAILED".equals(state)) {
            try {
                client.newWorkflowStub(OrderLifecycleWorkflow.class, "order-" + orderId).cancel(trimmed);
                return "CANCELLING";
            } catch (WorkflowNotFoundException finishedMeanwhile) {
                // The workflow closed between the state check and the signal; fall through and compensate directly.
            }
        }
        OrderWorkflowRequest request = new OrderWorkflowRequest(orderId, String.valueOf(order.get("customerId")), String.valueOf(order.get("productId")),
            String.valueOf(order.get("planId")), String.valueOf(order.get("planName")), decimal(order.get("total")), decimal(order.get("monthlyPrice")));
        try {
            String outcome = activities.compensate(request, trimmed);
            activities.publish(request, "order-" + orderId + "-cancel-" + Instant.now().toEpochMilli(), "ORDER_WORKFLOW", "CANCELLED",
                trimmed + (outcome.isBlank() ? "" : " (" + outcome + ")"));
        } catch (ApplicationFailure dependencyDown) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cancellation could not reach every service; please retry: " + dependencyDown.getOriginalMessage());
        }
        return "CANCELLED";
    }

    public boolean isCancelled(String orderId) { return "CANCELLED".equals(order(orderId).get("status")); }

    private Map<?, ?> order(String orderId) {
        try {
            return http.get().uri(orders + "/api/orders/{id}", orderId).retrieve().body(Map.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(String.valueOf(value)); }
}
