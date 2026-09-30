package com.example.orchestration.workflow;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface OrderActivities {
    void publish(OrderWorkflowRequest request, String eventId, String type, String status, String detail);
    String createPayment(OrderWorkflowRequest request);
    String paymentStatus(String orderId);
    String activate(OrderWorkflowRequest request);
    String fulfill(OrderWorkflowRequest request);
    String activationStatus(String orderId);
    String fulfillmentStatus(String orderId);
    void startBilling(OrderWorkflowRequest request, String activationId, String fulfillmentId);
    String billingStatus(String orderId);
    /** The operator's reason for the step's current status; step is PAYMENT, ESIM_ACTIVATION, FULFILLMENT or BILLING. */
    String rejectionReason(String step, String orderId);
    /**
     * Undoes what an order has done so far after an operator cancels it. Idempotent and best-effort per step;
     * returns a short summary for the order history.
     */
    String compensate(OrderWorkflowRequest request, String reason);
    /** Mirrors the saga's progress onto the order record in order-service; a refused transition is ignored. */
    void syncOrderStatus(String orderId, String status);
}
