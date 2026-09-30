package com.example.networkservice.service;

import com.example.networkservice.exception.ConflictException;
import com.example.networkservice.exception.ResourceNotFoundException;
import com.example.networkservice.model.*;
import com.example.networkservice.repository.ActivationRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ActivationService {
    private final ActivationRepository repository;
    private final long delaySeconds;
    private final boolean autoAdvance;

    public ActivationService(ActivationRepository repository,
            @Value("${network.activation-delay-seconds:30}") long delaySeconds,
            @Value("${network.auto-advance:false}") boolean autoAdvance) {
        this.repository = repository;
        this.delaySeconds = delaySeconds;
        this.autoAdvance = autoAdvance;
    }

    public Activation create(CreateActivationRequest request) {
        return repository.findByOrderId(request.orderId()).map(existing -> {
            if (Objects.equals(existing.getCustomerId(), request.customerId())
                    && Objects.equals(existing.getPlanId(), request.planId())) return existing;
            throw new ConflictException("An activation already exists for this order with different details");
        }).orElseGet(() -> {
            Instant now = Instant.now();
            Activation activation = new Activation();
            activation.setOrderId(request.orderId());
            activation.setCustomerId(request.customerId());
            activation.setPlanId(request.planId());
            activation.setIccid("8944" + UUID.randomUUID().toString().replace("-", "").substring(0, 15));
            activation.setStatus(ActivationStatus.QUEUED);
            activation.setRequestedAt(now);
            activation.setReadyAt(now.plusSeconds(delaySeconds));
            try {
                return repository.save(activation);
            } catch (DuplicateKeyException race) {
                return repository.findByOrderId(request.orderId())
                        .map(existing -> same(existing, request) ? existing : conflict())
                        .orElseThrow(() -> race);
            }
        });
    }

    private boolean same(Activation existing, CreateActivationRequest request) {
        return Objects.equals(existing.getCustomerId(), request.customerId())
                && Objects.equals(existing.getPlanId(), request.planId());
    }

    private Activation conflict() {
        throw new ConflictException("An activation already exists for this order with different details");
    }

    public List<Activation> list() { return repository.findAll(); }

    public Activation byOrder(String orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Activation not found for order " + orderId));
    }

    public Activation get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activation not found with ID " + id));
    }

    /** Operator approval of the current step: QUEUED -> ACTIVATING -> ACTIVE. */
    public Activation approve(String id, String reason) {
        Activation activation = get(id);
        Instant now = Instant.now();
        switch (activation.getStatus()) {
            case QUEUED -> activation.setStatus(ActivationStatus.ACTIVATING);
            case ACTIVATING -> {
                activation.setStatus(ActivationStatus.ACTIVE);
                activation.setActivatedAt(now);
            }
            default -> throw new ConflictException("Activation is already " + activation.getStatus() + " and cannot be advanced");
        }
        return record(activation, reason, now);
    }

    public Activation reject(String id, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to reject an activation");
        Activation activation = get(id);
        if (activation.getStatus() == ActivationStatus.ACTIVE || activation.getStatus() == ActivationStatus.FAILED) {
            throw new ConflictException("Activation is already " + activation.getStatus() + " and cannot be rejected");
        }
        activation.setStatus(ActivationStatus.FAILED);
        return record(activation, reason, Instant.now());
    }

    private Activation record(Activation activation, String reason, Instant now) {
        activation.setStatusReason(reason == null || reason.isBlank() ? null : reason.trim());
        activation.setStatusChangedAt(now);
        return repository.save(activation);
    }

    /** Simulated network provider; disabled by default so operators advance activations from the admin UI. */
    @Scheduled(fixedDelayString = "${network.processor-delay-ms:2000}")
    public void process() {
        if (!autoAdvance) return;
        Instant now = Instant.now();
        for (Activation activation : repository.findByStatusIn(List.of(ActivationStatus.QUEUED, ActivationStatus.ACTIVATING))) {
            if (activation.getStatus() == ActivationStatus.QUEUED) {
                activation.setStatus(ActivationStatus.ACTIVATING);
            } else if (!now.isBefore(activation.getReadyAt())) {
                activation.setStatus(ActivationStatus.ACTIVE);
                activation.setActivatedAt(now);
            } else continue;
            repository.save(activation);
        }
    }
}
