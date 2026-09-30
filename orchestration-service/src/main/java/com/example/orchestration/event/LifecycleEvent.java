package com.example.orchestration.event;

import java.time.Instant;

public record LifecycleEvent(
    int schemaVersion,
    String eventId,
    String orderId,
    String customerId,
    String eventType,
    String status,
    String detail,
    Instant occurredAt
) {}
