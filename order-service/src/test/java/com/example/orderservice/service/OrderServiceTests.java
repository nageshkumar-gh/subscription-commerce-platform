package com.example.orderservice.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.example.orderservice.model.OrderStatus;
class OrderServiceTests {
    @Test void calculatesDeviceAndFirstMonthTotal(){assertEquals(new BigDecimal("928.99"),OrderService.calculateTotal(new BigDecimal("899.00"),new BigDecimal("29.99")));}
    @Test void allowsValidStatusTransition(){assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.PENDING_PAYMENT,OrderStatus.PAID));}
    @Test void rejectsInvalidStatusTransition(){assertEquals(false,OrderService.isTransitionAllowed(OrderStatus.PENDING_PAYMENT,OrderStatus.DELIVERED));}
    @Test void terminalStatesCannotTransition(){assertEquals(false,OrderService.isTransitionAllowed(OrderStatus.COMPLETED,OrderStatus.CANCELLED));assertEquals(false,OrderService.isTransitionAllowed(OrderStatus.CANCELLED,OrderStatus.PENDING_PAYMENT));}
    @Test void followsTheDeliveryBeforeActivationFlow(){assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.PAID,OrderStatus.DISPATCHED));assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.DELIVERED,OrderStatus.ACTIVATED));assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.ACTIVATED,OrderStatus.COMPLETED));assertEquals(false,OrderService.isTransitionAllowed(OrderStatus.ACTIVATED,OrderStatus.DISPATCHED));}
    @Test void mayMoveForwardPastUnobservedSteps(){assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.PAID,OrderStatus.DELIVERED));}
    @Test void unfinishedOrdersCanBeCancelledOrFailed(){assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.DELIVERED,OrderStatus.CANCELLED));assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.PAID,OrderStatus.FAILED));assertEquals(true,OrderService.isTransitionAllowed(OrderStatus.FAILED,OrderStatus.CANCELLED));assertEquals(false,OrderService.isTransitionAllowed(OrderStatus.FAILED,OrderStatus.PAID));}
}
