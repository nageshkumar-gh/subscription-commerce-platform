package com.example.tracking;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import com.example.tracking.messaging.LifecycleEventConsumer;import com.example.tracking.model.LifecycleEvent;import com.example.tracking.repository.LifecycleEventRepository;import java.time.Instant;import org.apache.kafka.clients.consumer.ConsumerRecord;import org.junit.jupiter.api.Test;
class LifecycleEventConsumerTests{
 @Test void storesAnEventUsingItsStableId(){var repository=mock(LifecycleEventRepository.class);var consumer=new LifecycleEventConsumer(repository);var event=new LifecycleEvent(1,"event-1","order-1","customer-1","PAYMENT","COMPLETED",null,Instant.EPOCH);consumer.consume(new ConsumerRecord<>("events",0,1,"order-1",event));verify(repository).save(event);}
 @Test void rejectsAKeyThatCannotPreservePerOrderOrdering(){var repository=mock(LifecycleEventRepository.class);var consumer=new LifecycleEventConsumer(repository);var event=new LifecycleEvent(1,"event-1","order-1","customer-1","PAYMENT","COMPLETED",null,Instant.EPOCH);assertThatThrownBy(()->consumer.consume(new ConsumerRecord<>("events",0,1,"wrong",event))).isInstanceOf(IllegalArgumentException.class);verifyNoInteractions(repository);}
}
