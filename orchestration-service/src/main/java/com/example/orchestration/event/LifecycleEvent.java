package com.example.orchestration.event;import java.time.Instant;public record LifecycleEvent(String eventId,String orderId,String customerId,String type,String status,Instant occurredAt){}
