package com.example.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.example.orchestration.controller.OrderCancellation;
import com.example.orchestration.workflow.OrderActivities;
import com.example.orchestration.workflow.OrderLifecycleWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class OrderCancellationTests {
    private final WorkflowClient client = mock(WorkflowClient.class);
    private final OrderActivities activities = mock(OrderActivities.class);
    private final OrderLifecycleWorkflow workflow = mock(OrderLifecycleWorkflow.class);
    private MockRestServiceServer orderService;
    private OrderCancellation cancellation;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        orderService = MockRestServiceServer.bindTo(builder).build();
        cancellation = new OrderCancellation(client, activities, builder, "http://orders");
        when(client.newWorkflowStub(OrderLifecycleWorkflow.class, "order-order-1")).thenReturn(workflow);
    }

    private void order(String status) {
        orderService.expect(requestTo("http://orders/api/orders/order-1")).andRespond(withSuccess(
            "{\"id\":\"order-1\",\"customerId\":\"customer-1\",\"productId\":\"p\",\"planId\":\"plan\",\"planName\":\"Unlimited\",\"total\":10,\"monthlyPrice\":1,\"status\":\"" + status + "\"}", MediaType.APPLICATION_JSON));
    }

    @Test
    void orderWithoutWorkflowIsCompensatedDirectly() {
        order("PENDING_PAYMENT");
        when(workflow.status()).thenThrow(new WorkflowNotFoundException(WorkflowExecution.getDefaultInstance(), "OrderLifecycleWorkflow", null));
        when(activities.compensate(any(), eq("Changed my mind"))).thenReturn("payment voided");
        assertThat(cancellation.cancel("order-1", " Changed my mind ")).isEqualTo("CANCELLED");
        verify(activities).publish(argThat(r -> r.orderId().equals("order-1") && r.customerId().equals("customer-1")), startsWith("order-order-1-cancel-"), eq("ORDER_WORKFLOW"), eq("CANCELLED"), eq("Changed my mind (payment voided)"));
        verify(workflow, never()).cancel(any());
    }

    @Test
    void runningOrderIsCancelledByTheWorkflow() {
        order("PENDING_PAYMENT");
        when(workflow.status()).thenReturn("AWAITING_DELIVERY");
        assertThat(cancellation.cancel("order-1", "Customer request")).isEqualTo("CANCELLING");
        verify(workflow).cancel("Customer request");
        verify(activities, never()).compensate(any(), any());
    }

    @Test
    void completedOrderCannotBeCancelled() {
        order("PENDING_PAYMENT");
        when(workflow.status()).thenReturn("COMPLETED");
        assertThatThrownBy(() -> cancellation.cancel("order-1", "late")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("cancel its subscription instead");
        verify(activities, never()).compensate(any(), any());
    }

    @Test
    void alreadyCancelledOrderIsRejected() {
        order("CANCELLED");
        assertThatThrownBy(() -> cancellation.cancel("order-1", "again")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("already cancelled");
        verifyNoInteractions(activities);
    }
}
