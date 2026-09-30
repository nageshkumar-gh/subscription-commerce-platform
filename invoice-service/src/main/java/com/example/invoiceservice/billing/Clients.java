package com.example.invoiceservice.billing;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** HTTP clients for the services the invoicing run depends on. */
public final class Clients {
    private Clients() {}

    /** A subscription as billing-service reports it when due. */
    public record DueSubscription(String id, String orderId, String customerId, String planName, BigDecimal monthlyAmount,
                                  Integer billingDay, Instant nextBillingAt) {
        public LocalDate nextBillingDate() { return nextBillingAt.atZone(ZoneOffset.UTC).toLocalDate(); }
    }

    public record Charge(String id, String status, String transactionReference, String statusReason) {
        public boolean paid() { return "COMPLETED".equals(status); }
    }

    @Component
    public static class BillingClient {
        private static final int PAGE_SIZE = 200;
        private final RestClient http;

        public BillingClient(RestClient.Builder builder, @Value("${services.billing-url}") String baseUrl) {
            this.http = builder.baseUrl(baseUrl).build();
        }

        /** Every ACTIVE subscription due on or before {@code asOf}. */
        public List<DueSubscription> due(LocalDate asOf) {
            List<DueSubscription> all = new ArrayList<>();
            for (int page = 0; ; page++) {
                DueSubscription[] batch = http.get().uri("/api/subscriptions/due?asOf={asOf}&page={page}&size={size}", asOf, page, PAGE_SIZE)
                    .retrieve().body(DueSubscription[].class);
                if (batch == null || batch.length == 0) return all;
                all.addAll(Arrays.asList(batch));
                if (batch.length < PAGE_SIZE) return all;
            }
        }

        /** Moves the subscription's next billing date past an invoiced period; idempotent on billing-service's side. */
        public void recordBilledPeriod(String orderId, LocalDate start, LocalDate end) {
            http.post().uri("/api/subscriptions/{orderId}/billing-periods", orderId)
                .body(Map.of("periodStart", start.toString(), "periodEnd", end.toString())).retrieve().toBodilessEntity();
        }
    }

    @Component
    public static class PaymentClient {
        private final RestClient http;

        public PaymentClient(RestClient.Builder builder, @Value("${services.payment-url}") String baseUrl) {
            this.http = builder.baseUrl(baseUrl).build();
        }

        /** Charges one invoice; the invoice number is the idempotency key, so a retried run never charges twice. */
        public Charge charge(String invoiceNumber, String orderId, String customerId, BigDecimal amount, String currency) {
            return http.post().uri("/api/payments/charges").header("Idempotency-Key", "invoice-" + invoiceNumber)
                .body(Map.of("invoiceId", invoiceNumber, "orderId", orderId, "customerId", customerId, "amount", amount, "currency", currency))
                .retrieve().body(Charge.class);
        }
    }
}
