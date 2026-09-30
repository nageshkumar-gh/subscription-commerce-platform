package com.example.tracking.model;

import java.time.Instant;

public record OrderTrackingSummary(
    String orderId,
    String customerId,
    String workflowStatus,
    String paymentStatus,
    String activationStatus,
    String fulfillmentStatus,
    String billingStatus,
    Instant updatedAt
) {}
