package com.example.tracking.model;
import java.time.Instant;import org.springframework.data.annotation.Id;import org.springframework.data.mongodb.core.index.Indexed;import org.springframework.data.mongodb.core.mapping.Document;
@Document("lifecycle_events") public record LifecycleEvent(int schemaVersion,@Id String eventId,@Indexed String orderId,@Indexed String customerId,String eventType,String status,String detail,Instant occurredAt){}
