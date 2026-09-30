package com.example.billingservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class DependencyClient {
    private final RestClient client;
    private final String payment;
    private final String network;
    private final String fulfillment;

    public DependencyClient(@Value("${services.payment-url}") String payment,
                            @Value("${services.network-url}") String network,
                            @Value("${services.fulfillment-url}") String fulfillment) {
        this.client = RestClient.create();
        this.payment = payment;
        this.network = network;
        this.fulfillment = fulfillment;
    }

    public DependencyState payment(String orderId) {
        return state(payment, "/api/payments", orderId);
    }

    public DependencyState activation(String orderId) {
        return state(network, "/api/activations", orderId);
    }

    public DependencyState fulfillment(String orderId) {
        return state(fulfillment, "/api/fulfillments", orderId);
    }

    private DependencyState state(String baseUrl, String path, String orderId) {
        try {
            DependencyState response = client.get()
                    .uri(baseUrl + path + "?orderId={orderId}", orderId)
                    .retrieve()
                    .body(DependencyState.class);
            return response == null ? DependencyState.unavailable() : response;
        } catch (Exception ignored) {
            return DependencyState.unavailable();
        }
    }

    public record DependencyState(String id, String status) {
        static DependencyState unavailable() {
            return new DependencyState(null, "UNAVAILABLE");
        }
    }
}
