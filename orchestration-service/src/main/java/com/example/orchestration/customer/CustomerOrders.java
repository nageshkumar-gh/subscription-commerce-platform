package com.example.orchestration.customer;

import com.example.orchestration.controller.OrderCancellation;
import com.example.orchestration.controller.OrderWorkflows;
import com.example.orchestration.workflow.OrderWorkflowRequest;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

/**
 * Everything a signed-in customer can do with their orders. Every method takes the customer id from the verified
 * token and refuses orders that belong to someone else (reported as not found, so order ids cannot be probed).
 * Prices always come from the product catalogue, never from the browser.
 */
@Service
public class CustomerOrders {
    private final RestClient http;
    private final OrderWorkflows workflows;
    private final OrderCancellation cancellation;
    private final String orders, products, payments, network, fulfillment, billing, tracking;

    public CustomerOrders(RestClient.Builder http, OrderWorkflows workflows, OrderCancellation cancellation,
                          @Value("${services.order-url}") String orders, @Value("${services.product-url}") String products,
                          @Value("${services.payment-url}") String payments, @Value("${services.network-url}") String network,
                          @Value("${services.fulfillment-url}") String fulfillment, @Value("${services.billing-url}") String billing,
                          @Value("${services.tracking-url}") String tracking) {
        this.http = http.build();
        this.workflows = workflows;
        this.cancellation = cancellation;
        this.orders = orders; this.products = products; this.payments = payments; this.network = network;
        this.fulfillment = fulfillment; this.billing = billing; this.tracking = tracking;
    }

    public Map<?, ?> place(String customerId, String productId, String planId) {
        Map<?, ?> product = find(products + "/api/products/{id}", productId);
        if (product == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That phone is not available");
        Map<?, ?> plan = Arrays.stream(Objects.requireNonNull(http.get().uri(products + "/api/esim-plans").retrieve().body(Map[].class)))
            .filter(candidate -> planId.equals(candidate.get("id"))).findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "That plan is not available"));
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("customerId", customerId);
        order.put("productId", productId);
        order.put("productName", product.get("name"));
        order.put("storage", product.get("storage"));
        order.put("planId", planId);
        order.put("planName", plan.get("name"));
        order.put("devicePrice", product.get("price"));
        order.put("monthlyPrice", plan.get("monthlyPrice"));
        return http.post().uri(orders + "/api/orders").body(order).retrieve().body(Map.class);
    }

    /** Starts the order's lifecycle (checkout). Safe to repeat. */
    public String checkout(String customerId, String orderId) {
        Map<?, ?> order = owned(customerId, orderId);
        return workflows.start(new OrderWorkflowRequest(orderId, customerId, String.valueOf(order.get("productId")), String.valueOf(order.get("planId")),
            String.valueOf(order.get("planName")), decimal(order.get("total")), decimal(order.get("monthlyPrice"))));
    }

    public List<Map<?, ?>> list(String customerId) {
        Map<?, ?>[] found = http.get().uri(orders + "/api/orders?customerId={id}", customerId).retrieve().body(Map[].class);
        return Arrays.stream(found == null ? new Map<?, ?>[0] : found)
            .sorted(Comparator.comparing((Map<?, ?> order) -> String.valueOf(order.get("createdAt"))).reversed()).toList();
    }

    /** The order with every step record (null when not started yet) and its lifecycle history. */
    public Map<String, Object> details(String customerId, String orderId) {
        Map<?, ?> order = owned(customerId, orderId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("order", order);
        details.put("payment", find(payments + "/api/payments?orderId={id}", orderId));
        details.put("delivery", find(fulfillment + "/api/fulfillments?orderId={id}", orderId));
        details.put("activation", find(network + "/api/activations?orderId={id}", orderId));
        details.put("billing", find(billing + "/api/subscriptions?orderId={id}", orderId));
        details.put("events", history(orderId));
        return details;
    }

    public List<?> events(String customerId, String orderId) {
        owned(customerId, orderId);
        return history(orderId);
    }

    /** Customers may cancel until their subscription is active; after that it is managed as a subscription. */
    public String cancel(String customerId, String orderId, String reason) {
        owned(customerId, orderId);
        Map<?, ?> subscription = find(billing + "/api/subscriptions?orderId={id}", orderId);
        if (subscription != null && "ACTIVE".equals(subscription.get("status"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Your subscription is already active; contact support to cancel it");
        }
        return cancellation.cancel(orderId, "Customer: " + reason.trim());
    }

    public List<?> activeSubscriptions(String customerId) {
        Map<?, ?>[] found = http.get().uri(billing + "/api/subscriptions?customerId={id}&status=ACTIVE", customerId).retrieve().body(Map[].class);
        return found == null ? List.of() : List.of(found);
    }

    private Map<?, ?> owned(String customerId, String orderId) {
        Map<?, ?> order = find(orders + "/api/orders/{id}", orderId);
        if (order == null || !customerId.equals(order.get("customerId"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        return order;
    }

    private List<?> history(String orderId) {
        try {
            Map<?, ?>[] events = http.get().uri(tracking + "/api/tracking/orders/{id}/events", orderId).retrieve().body(Map[].class);
            return events == null ? List.of() : List.of(events);
        } catch (RuntimeException trackingUnavailable) {
            return List.of();
        }
    }

    private Map<?, ?> find(String url, String id) {
        try {
            return http.get().uri(url, id).retrieve().body(Map.class);
        } catch (HttpClientErrorException.NotFound notFound) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) { return value == null ? null : new BigDecimal(String.valueOf(value)); }
}
