package com.example.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.orchestration.workflow.*;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class OrderLifecycleWorkflowTests {
    private final OrderWorkflowRequest request = new OrderWorkflowRequest("order-1","customer-1","product-1","plan-1","Unlimited",BigDecimal.TEN,BigDecimal.ONE);

    @Test
    void completesOnlyAfterEveryStepIsApproved() {
        OrderActivities activities = approvedActivities();
        assertThat(run(activities)).isEqualTo("COMPLETED");
        var order = inOrder(activities);
        order.verify(activities).createPayment(request);
        order.verify(activities).fulfill(request);
        order.verify(activities, atLeastOnce()).fulfillmentStatus("order-1");
        order.verify(activities).activate(request);
        order.verify(activities).startBilling(request,"activation-1","fulfillment-1");
        verify(activities, times(1)).publish(eq(request),anyString(),eq("PAYMENT"),eq("PENDING"),any());
        var orderStatuses = inOrder(activities);
        for (String status : new String[]{"PAID", "DISPATCHED", "DELIVERED", "ACTIVATED", "COMPLETED"}) orderStatuses.verify(activities).syncOrderStatus("order-1", status);
        verify(activities, never()).syncOrderStatus("order-1", "FAILED");
        verify(activities).startBilling(request,"activation-1","fulfillment-1");
        verify(activities).publish(eq(request),contains("PAYMENT-COMPLETED"),eq("PAYMENT"),eq("COMPLETED"),isNull());
        verify(activities).publish(eq(request),contains("ESIM_ACTIVATION-ACTIVATING"),eq("ESIM_ACTIVATION"),eq("ACTIVATING"),isNull());
        verify(activities).publish(eq(request),contains("FULFILLMENT-DISPATCHED"),eq("FULFILLMENT"),eq("DISPATCHED"),isNull());
        verify(activities).publish(eq(request),contains("BILLING-READY_FOR_BILLING"),eq("BILLING"),eq("READY_FOR_BILLING"),isNull());
        verify(activities).publish(eq(request),contains("BILLING-ACTIVE"),eq("BILLING"),eq("ACTIVE"),isNull());
    }

    @Test
    void rejectedPaymentFailsOrderWithOperatorReason() {
        OrderActivities activities = approvedActivities();
        when(activities.paymentStatus("order-1")).thenReturn("PENDING", "FAILED");
        when(activities.rejectionReason("PAYMENT","order-1")).thenReturn("Card flagged as stolen");
        assertThatThrownBy(() -> run(activities)).isInstanceOf(WorkflowFailedException.class)
            .rootCause().hasMessageContaining("Payment ended in FAILED: Card flagged as stolen");
        verify(activities).publish(eq(request),contains("PAYMENT-FAILED"),eq("PAYMENT"),eq("FAILED"),eq("Card flagged as stolen"));
        verify(activities, never()).activate(any());
    }

    @Test
    void rejectedDeliveryFailsOrderBeforeActivatingTheEsim() {
        OrderActivities activities = approvedActivities();
        when(activities.fulfillmentStatus("order-1")).thenReturn("PREPARING", "FAILED");
        when(activities.rejectionReason("FULFILLMENT","order-1")).thenReturn("Address undeliverable");
        assertThatThrownBy(() -> run(activities)).rootCause().hasMessageContaining("Delivery ended in FAILED: Address undeliverable");
        verify(activities, never()).activate(any());
        verify(activities, never()).startBilling(any(), any(), any());
        verify(activities).syncOrderStatus("order-1", "FAILED");
    }

    @Test
    void rejectedBillingFailsOrder() {
        OrderActivities activities = approvedActivities();
        when(activities.billingStatus("order-1")).thenReturn("READY_FOR_BILLING", "REJECTED");
        when(activities.rejectionReason("BILLING","order-1")).thenReturn("Plan price dispute");
        assertThatThrownBy(() -> run(activities)).rootCause().hasMessageContaining("Billing ended in REJECTED: Plan price dispute");
        verify(activities).publish(eq(request),contains("ORDER_WORKFLOW-FAILED"),eq("ORDER_WORKFLOW"),eq("FAILED"),contains("Plan price dispute"));
    }

    @Test
    void cancellationStopsTheOrderAndCompensates() {
        OrderActivities activities = approvedActivities();
        when(activities.paymentStatus("order-1")).thenReturn("COMPLETED");
        when(activities.fulfillmentStatus("order-1")).thenReturn("PREPARING");
        when(activities.compensate(any(), any())).thenReturn("refund requested; delivery stopped");
        try (var environment = TestWorkflowEnvironment.newInstance()) {
            var worker = environment.newWorker("order-lifecycle");
            worker.registerWorkflowImplementationTypes(OrderLifecycleWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            environment.start();
            var workflow = environment.getWorkflowClient().newWorkflowStub(OrderLifecycleWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId("order-order-1").setTaskQueue("order-lifecycle").build());
            WorkflowClient.start(workflow::run, request);
            environment.sleep(java.time.Duration.ofMinutes(1));
            assertThat(workflow.status()).isEqualTo("AWAITING_DELIVERY");
            workflow.cancel("Customer changed their mind");
            assertThat(WorkflowStub.fromTyped(workflow).getResult(String.class)).isEqualTo("CANCELLED");
        }
        verify(activities).compensate(request, "Customer changed their mind");
        verify(activities, never()).activate(any());
        verify(activities).publish(eq(request),contains("ORDER_WORKFLOW-CANCELLED"),eq("ORDER_WORKFLOW"),eq("CANCELLED"),eq("Customer changed their mind (refund requested; delivery stopped)"));
    }

    private OrderActivities approvedActivities() {
        OrderActivities activities = mock(OrderActivities.class);
        when(activities.createPayment(any())).thenReturn("PENDING");
        when(activities.paymentStatus("order-1")).thenReturn("PENDING", "COMPLETED");
        when(activities.activate(any())).thenReturn("activation-1");
        when(activities.fulfill(any())).thenReturn("fulfillment-1");
        when(activities.activationStatus("order-1")).thenReturn("ACTIVATING", "ACTIVE");
        when(activities.fulfillmentStatus("order-1")).thenReturn("PREPARING", "DISPATCHED", "DELIVERED");
        when(activities.billingStatus("order-1")).thenReturn("READY_FOR_BILLING", "ACTIVE");
        return activities;
    }

    private String run(OrderActivities activities) {
        try (var environment = TestWorkflowEnvironment.newInstance()) {
            var worker = environment.newWorker("order-lifecycle");
            worker.registerWorkflowImplementationTypes(OrderLifecycleWorkflowImpl.class);
            worker.registerActivitiesImplementations(activities);
            environment.start();
            var workflow = environment.getWorkflowClient().newWorkflowStub(OrderLifecycleWorkflow.class,
                WorkflowOptions.newBuilder().setWorkflowId("order-order-1").setTaskQueue("order-lifecycle").build());
            return workflow.run(request);
        }
    }
}
