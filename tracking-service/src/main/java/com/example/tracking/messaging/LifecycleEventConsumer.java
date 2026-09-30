package com.example.tracking.messaging;
import com.example.tracking.model.LifecycleEvent;import com.example.tracking.repository.LifecycleEventRepository;import org.apache.kafka.clients.consumer.ConsumerRecord;import org.springframework.kafka.annotation.KafkaListener;import org.springframework.stereotype.Component;
@Component public class LifecycleEventConsumer{
 private final LifecycleEventRepository repository; public LifecycleEventConsumer(LifecycleEventRepository repository){this.repository=repository;}
 @KafkaListener(topics="order-lifecycle-events",groupId="tracking-service") public void consume(ConsumerRecord<String,LifecycleEvent> record){LifecycleEvent event=record.value();if(event==null||event.eventId()==null||event.orderId()==null||event.customerId()==null||event.eventType()==null||event.status()==null||event.occurredAt()==null)throw new IllegalArgumentException("Lifecycle event fields are required");if(event.schemaVersion()!=1)throw new IllegalArgumentException("Unsupported lifecycle event schema version: "+event.schemaVersion());if(!event.orderId().equals(record.key()))throw new IllegalArgumentException("Kafka key must equal orderId");repository.save(event);}
}
