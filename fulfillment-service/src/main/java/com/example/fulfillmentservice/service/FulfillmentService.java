package com.example.fulfillmentservice.service;

import com.example.fulfillmentservice.exception.*;
import com.example.fulfillmentservice.model.*;
import com.example.fulfillmentservice.repository.FulfillmentRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class FulfillmentService {
    private final FulfillmentRepository repository;
    private final long delaySeconds;
    private final boolean autoAdvance;
    public FulfillmentService(FulfillmentRepository repository,@Value("${fulfillment.transition-delay-seconds:15}") long delaySeconds,@Value("${fulfillment.auto-advance:false}") boolean autoAdvance){this.repository=repository;this.delaySeconds=delaySeconds;this.autoAdvance=autoAdvance;}

    public Fulfillment create(CreateFulfillmentRequest request){
        return repository.findByOrderId(request.orderId()).map(existing->{
            if(Objects.equals(existing.getCustomerId(),request.customerId())&&Objects.equals(existing.getProductId(),request.productId()))return existing;
            throw new ConflictException("A fulfillment already exists for this order with different details");
        }).orElseGet(()->{
            Instant now=Instant.now(); Fulfillment fulfillment=new Fulfillment();
            fulfillment.setOrderId(request.orderId());fulfillment.setCustomerId(request.customerId());fulfillment.setProductId(request.productId());
            fulfillment.setTrackingNumber("TRACK-"+UUID.randomUUID().toString().substring(0,8).toUpperCase());
            fulfillment.setStatus(FulfillmentStatus.RECEIVED);fulfillment.setCreatedAt(now);fulfillment.setNextTransitionAt(now.plusSeconds(delaySeconds));
            try {
                return repository.save(fulfillment);
            } catch (DuplicateKeyException race) {
                return repository.findByOrderId(request.orderId())
                        .map(existing -> same(existing, request) ? existing : conflict())
                        .orElseThrow(() -> race);
            }
        });
    }
    private boolean same(Fulfillment existing,CreateFulfillmentRequest request){return Objects.equals(existing.getCustomerId(),request.customerId())&&Objects.equals(existing.getProductId(),request.productId());}
    private Fulfillment conflict(){throw new ConflictException("A fulfillment already exists for this order with different details");}
    public List<Fulfillment> list(){return repository.findAll();}
    public Fulfillment byOrder(String orderId){return repository.findByOrderId(orderId).orElseThrow(()->new ResourceNotFoundException("Fulfillment not found for order "+orderId));}

    public Fulfillment get(String id){return repository.findById(id).orElseThrow(()->new ResourceNotFoundException("Fulfillment not found with ID "+id));}

    /** Operator approval of the current step: RECEIVED -> PREPARING -> DISPATCHED -> DELIVERED. */
    public Fulfillment approve(String id,String reason){
        Fulfillment fulfillment=get(id);Instant now=Instant.now();
        switch(fulfillment.getStatus()){
            case RECEIVED -> fulfillment.setStatus(FulfillmentStatus.PREPARING);
            case PREPARING -> fulfillment.setStatus(FulfillmentStatus.DISPATCHED);
            case DISPATCHED -> {fulfillment.setStatus(FulfillmentStatus.DELIVERED);fulfillment.setDeliveredAt(now);}
            default -> throw new ConflictException("Fulfillment is already "+fulfillment.getStatus()+" and cannot be advanced");
        }
        fulfillment.setNextTransitionAt(null);
        return record(fulfillment,reason,now);
    }
    public Fulfillment reject(String id,String reason){
        if(reason==null||reason.isBlank())throw new IllegalArgumentException("A reason is required to reject a fulfillment");
        Fulfillment fulfillment=get(id);
        if(fulfillment.getStatus()==FulfillmentStatus.DELIVERED||fulfillment.getStatus()==FulfillmentStatus.FAILED)throw new ConflictException("Fulfillment is already "+fulfillment.getStatus()+" and cannot be rejected");
        fulfillment.setStatus(FulfillmentStatus.FAILED);fulfillment.setNextTransitionAt(null);
        return record(fulfillment,reason,Instant.now());
    }
    private Fulfillment record(Fulfillment fulfillment,String reason,Instant now){fulfillment.setStatusReason(reason==null||reason.isBlank()?null:reason.trim());fulfillment.setStatusChangedAt(now);return repository.save(fulfillment);}

    /** Simulated courier; disabled by default so operators advance deliveries from the admin UI. */
    @Scheduled(fixedDelayString="${fulfillment.processor-delay-ms:2000}")
    public void process(){
        if(!autoAdvance)return;
        Instant now=Instant.now();
        for(Fulfillment fulfillment:repository.findByStatusIn(List.of(FulfillmentStatus.RECEIVED,FulfillmentStatus.PREPARING,FulfillmentStatus.DISPATCHED))){
            if(fulfillment.getNextTransitionAt()==null||now.isBefore(fulfillment.getNextTransitionAt()))continue;
            switch(fulfillment.getStatus()){
                case RECEIVED -> fulfillment.setStatus(FulfillmentStatus.PREPARING);
                case PREPARING -> fulfillment.setStatus(FulfillmentStatus.DISPATCHED);
                case DISPATCHED -> {fulfillment.setStatus(FulfillmentStatus.DELIVERED);fulfillment.setDeliveredAt(now);}
                default -> {continue;}
            }
            fulfillment.setNextTransitionAt(fulfillment.getStatus()==FulfillmentStatus.DELIVERED?null:now.plusSeconds(delaySeconds));
            repository.save(fulfillment);
        }
    }
}
