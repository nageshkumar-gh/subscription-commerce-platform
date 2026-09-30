package com.example.orchestration.workflow;

import com.example.orchestration.event.LifecycleEvent;
import io.temporal.failure.ApplicationFailure;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Component
public class OrderActivitiesImpl implements OrderActivities {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderActivitiesImpl.class);
    private static final String TOPIC = "order-lifecycle-events";
    private final RestClient http;
    private final KafkaTemplate<String, LifecycleEvent> kafka;
    private final String payment;
    private final String network;
    private final String fulfillment;
    private final String billing;
    private final String orders;

    public OrderActivitiesImpl(RestClient.Builder http, KafkaTemplate<String, LifecycleEvent> kafka,
        @Value("${services.payment-url}") String payment,
        @Value("${services.network-url}") String network,
        @Value("${services.fulfillment-url}") String fulfillment,
        @Value("${services.billing-url}") String billing,
        @Value("${services.order-url}") String orders) {
        this.http = http.build(); this.kafka = kafka; this.payment = payment;
        this.network = network; this.fulfillment = fulfillment; this.billing = billing; this.orders = orders;
    }

    public void publish(OrderWorkflowRequest r, String eventId, String type, String status, String detail) {
        kafka.send(TOPIC, r.orderId(), new LifecycleEvent(1, eventId, r.orderId(), r.customerId(), type, status, detail, Instant.now())).join();
    }
    public String createPayment(OrderWorkflowRequest r) {
        Map response = post(payment + "/api/payments", Map.of("orderId", r.orderId(), "customerId", r.customerId(), "amount", r.total(), "currency", "EUR", "paymentMethod", "SIMULATED_CARD", "paymentToken", "workflow-token"), Map.class, Map.of("Idempotency-Key", "order-" + r.orderId()));
        return text(response.get("status"));
    }
    public String paymentStatus(String id) { return status(payment + "/api/payments?orderId=" + id); }
    public String activate(OrderWorkflowRequest r) { return text(post(network + "/api/activations", Map.of("orderId", r.orderId(), "customerId", r.customerId(), "planId", r.planId()), Map.class, Map.of()).get("id")); }
    public String fulfill(OrderWorkflowRequest r) { return text(post(fulfillment + "/api/fulfillments", Map.of("orderId", r.orderId(), "customerId", r.customerId(), "productId", r.productId()), Map.class, Map.of()).get("id")); }
    public String activationStatus(String id) { return status(network + "/api/activations?orderId=" + id); }
    public String fulfillmentStatus(String id) { return status(fulfillment + "/api/fulfillments?orderId=" + id); }
    public void startBilling(OrderWorkflowRequest r, String a, String f) { post(billing + "/api/subscriptions", Map.of("orderId", r.orderId(), "customerId", r.customerId(), "planName", r.planName(), "monthlyAmount", r.monthlyAmount(), "activationId", a, "fulfillmentId", f), Map.class, Map.of()); }

    public String billingStatus(String id) { return status(billing + "/api/subscriptions?orderId=" + id); }
    public String rejectionReason(String step, String id) {
        String url = switch (step) {
            case "PAYMENT" -> payment + "/api/payments?orderId=" + id;
            case "ESIM_ACTIVATION" -> network + "/api/activations?orderId=" + id;
            case "FULFILLMENT" -> fulfillment + "/api/fulfillments?orderId=" + id;
            case "BILLING" -> billing + "/api/subscriptions?orderId=" + id;
            default -> throw ApplicationFailure.newNonRetryableFailure("Unknown step " + step, "INVALID_STEP");
        };
        try { Object reason = http.get().uri(url).retrieve().body(Map.class).get("statusReason"); return reason == null ? null : String.valueOf(reason); } catch (HttpStatusCodeException e) { throw mappedFailure(e); }
    }

    public String compensate(OrderWorkflowRequest r, String reason) {
        String id = r.orderId();
        Map<String, String> because = Map.of("reason", reason);
        List<String> outcome = new ArrayList<>();
        if (record(billing + "/api/subscriptions?orderId=" + id) != null && bestEffort(() -> post(billing + "/api/subscriptions/" + id + "/cancel", Map.of(), Map.class, Map.of()))) outcome.add("billing cancelled");
        Map activation = record(network + "/api/activations?orderId=" + id);
        if (activation != null) {
            if ("ACTIVE".equals(activation.get("status"))) outcome.add("eSIM is active and must be deactivated manually");
            else if (!"FAILED".equals(activation.get("status")) && bestEffort(() -> post(network + "/api/activations/" + activation.get("id") + "/reject", because, Map.class, Map.of()))) outcome.add("eSIM activation stopped");
        }
        Map delivery = record(fulfillment + "/api/fulfillments?orderId=" + id);
        if (delivery != null) {
            if ("DELIVERED".equals(delivery.get("status"))) outcome.add("device already delivered; arrange a return");
            else if (!"FAILED".equals(delivery.get("status")) && bestEffort(() -> post(fulfillment + "/api/fulfillments/" + delivery.get("id") + "/reject", because, Map.class, Map.of()))) outcome.add("delivery stopped");
        }
        Map charge = record(payment + "/api/payments?orderId=" + id);
        if (charge != null) {
            Object status = charge.get("status");
            if ("PENDING".equals(status) && bestEffort(() -> post(payment + "/api/payments/" + charge.get("id") + "/reject", because, Map.class, Map.of()))) outcome.add("payment voided");
            else if ("COMPLETED".equals(status) && bestEffort(() -> post(payment + "/api/payments/" + charge.get("id") + "/refund", because, Map.class, Map.of()))) outcome.add("refund requested");
        }
        bestEffort(() -> http.patch().uri(orders + "/api/orders/" + id + "/status").body(Map.of("status", "CANCELLED")).retrieve().toBodilessEntity());
        return String.join("; ", outcome);
    }

    public void syncOrderStatus(String orderId, String status) {
        if (!bestEffort(() -> http.patch().uri(orders + "/api/orders/" + orderId + "/status").body(Map.of("status", status)).retrieve().toBodilessEntity())) {
            log.warn("Order {} was not moved to {}: order-service refused the transition", orderId, status);
        }
    }

    /** GETs a step record, or null when the step was never started. */
    private Map record(String url) {
        try { return http.get().uri(url).retrieve().body(Map.class); }
        catch (HttpStatusCodeException e) { if (e.getStatusCode().value() == 404) return null; throw mappedFailure(e); }
    }
    /**
     * Runs a compensating call. A 4xx means the step is already closed (e.g. already refunded), which is fine;
     * outages propagate so Temporal retries the activity.
     */
    private boolean bestEffort(Runnable call) {
        try { call.run(); return true; }
        catch (ApplicationFailure e) { if ("DEPENDENCY_REJECTED".equals(e.getType())) return false; throw e; }
        catch (HttpStatusCodeException e) { if (e.getStatusCode().is4xxClientError()) return false; throw mappedFailure(e); }
    }

    private String status(String url) { try { return text(http.get().uri(url).retrieve().body(Map.class).get("status")); } catch (HttpStatusCodeException e) { throw mappedFailure(e); } }
    private <T> T post(String url, Object body, Class<T> type, Map<String,String> headers) { try { return http.post().uri(url).headers(h -> headers.forEach(h::set)).body(body).retrieve().body(type); } catch (HttpStatusCodeException e) { throw mappedFailure(e); } }
    private RuntimeException mappedFailure(HttpStatusCodeException e) { HttpStatusCode status=e.getStatusCode(); String message="Dependency returned HTTP "+status.value(); return status.is4xxClientError()?ApplicationFailure.newNonRetryableFailure(message,"DEPENDENCY_REJECTED"):ApplicationFailure.newFailure(message,"DEPENDENCY_UNAVAILABLE"); }
    private String text(Object value) { if(value==null) throw ApplicationFailure.newNonRetryableFailure("Dependency response omitted a required field","INVALID_DEPENDENCY_RESPONSE"); return String.valueOf(value); }
}
