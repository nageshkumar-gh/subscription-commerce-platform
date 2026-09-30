package com.example.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.example.orchestration.controller.OrderCancellation;
import com.example.orchestration.controller.OrderWorkflows;
import com.example.orchestration.customer.CustomerOrders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class CustomerOrdersTests {
    private MockRestServiceServer server;
    private final OrderWorkflows workflows = mock(OrderWorkflows.class);
    private final OrderCancellation cancellation = mock(OrderCancellation.class);
    private CustomerOrders customerOrders;

    @BeforeEach
    void setup() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        customerOrders = new CustomerOrders(builder, workflows, cancellation, "http://orders", "http://products", "http://payments", "http://network",
            "http://fulfillment", "http://billing", "http://tracking");
    }

    private void order(String customerId) {
        server.expect(requestTo("http://orders/api/orders/order-1")).andRespond(withSuccess(
            "{\"id\":\"order-1\",\"customerId\":\"" + customerId + "\",\"productId\":\"p1\",\"planId\":\"plan-1\",\"planName\":\"Unlimited\",\"total\":1328.99,\"monthlyPrice\":29.99}", MediaType.APPLICATION_JSON));
    }

    @Test
    void anotherCustomersOrderIsNotFound() {
        order("someone-else");
        assertThatThrownBy(() -> customerOrders.details("customer-1", "order-1")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        verifyNoInteractions(cancellation, workflows);
    }

    @Test
    void placesOrdersAtCataloguePrices() {
        server.expect(requestTo("http://products/api/products/p1")).andRespond(withSuccess("{\"id\":\"p1\",\"name\":\"iPhone 18 Pro Max\",\"storage\":\"1 TB\",\"price\":1299.00}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://products/api/esim-plans")).andRespond(withSuccess("[{\"id\":\"plan-1\",\"name\":\"Unlimited\",\"monthlyPrice\":29.99}]", MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://orders/api/orders")).andExpect(method(HttpMethod.POST))
            .andExpect(jsonPath("$.customerId").value("customer-1")).andExpect(jsonPath("$.devicePrice").value(1299.00)).andExpect(jsonPath("$.monthlyPrice").value(29.99))
            .andRespond(withSuccess("{\"id\":\"order-1\"}", MediaType.APPLICATION_JSON));
        assertThat(customerOrders.place("customer-1", "p1", "plan-1").get("id")).isEqualTo("order-1");
        server.verify();
    }

    @Test
    void cannotCancelOnceTheSubscriptionIsActive() {
        order("customer-1");
        server.expect(requestTo("http://billing/api/subscriptions?orderId=order-1")).andRespond(withSuccess("{\"status\":\"ACTIVE\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> customerOrders.cancel("customer-1", "order-1", "changed my mind")).isInstanceOf(ResponseStatusException.class).hasMessageContaining("409");
        verifyNoInteractions(cancellation);
    }

    @Test
    void cancelsOwnOrderWithCustomerReason() {
        order("customer-1");
        server.expect(requestTo("http://billing/api/subscriptions?orderId=order-1")).andRespond(withResourceNotFound());
        when(cancellation.cancel("order-1", "Customer: changed my mind")).thenReturn("CANCELLED");
        assertThat(customerOrders.cancel("customer-1", "order-1", " changed my mind ")).isEqualTo("CANCELLED");
    }
}
