package com.example.billingservice.service;

import com.example.billingservice.exception.ConflictException;
import com.example.billingservice.exception.ResourceNotFoundException;
import com.example.billingservice.model.BillingStatus;
import com.example.billingservice.model.CreateSubscriptionRequest;
import com.example.billingservice.model.Subscription;
import com.example.billingservice.repository.SubscriptionRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class BillingService {
    private static final List<BillingStatus> AWAITING_APPROVAL = List.of(
            BillingStatus.WAITING_FOR_FULFILLMENT, BillingStatus.WAITING_FOR_ACTIVATION, BillingStatus.READY_FOR_BILLING);
    private final SubscriptionRepository repository;
    private final DependencyClient dependencies;

    public BillingService(SubscriptionRepository repository, DependencyClient dependencies) {
        this.repository = repository;
        this.dependencies = dependencies;
    }

    public Subscription create(CreateSubscriptionRequest request) {
        return repository.findByOrderId(request.orderId())
                .map(existing -> same(existing, request) ? existing : conflict())
                .orElseGet(() -> createNew(request));
    }

    private Subscription createNew(CreateSubscriptionRequest request) {
        validateDependencyId("activation", request.activationId(), dependencies.activation(request.orderId()));
        validateDependencyId("fulfillment", request.fulfillmentId(), dependencies.fulfillment(request.orderId()));

        Subscription subscription = new Subscription();
        subscription.setOrderId(request.orderId());
        subscription.setCustomerId(request.customerId());
        subscription.setPlanName(request.planName());
        subscription.setMonthlyAmount(request.monthlyAmount());
        subscription.setActivationId(request.activationId());
        subscription.setFulfillmentId(request.fulfillmentId());
        subscription.setStatus(BillingStatus.WAITING_FOR_FULFILLMENT);
        subscription.setCreatedAt(Instant.now());
        try {
            return repository.save(subscription);
        } catch (DuplicateKeyException race) {
            return repository.findByOrderId(request.orderId())
                    .map(existing -> same(existing, request) ? existing : conflict())
                    .orElseThrow(() -> race);
        }
    }

    private void validateDependencyId(String name, String expectedId, DependencyClient.DependencyState actual) {
        if (actual.id() != null && !Objects.equals(expectedId, actual.id())) {
            throw new ConflictException("The " + name + " ID does not match the dependency for this order");
        }
    }

    private boolean same(Subscription subscription, CreateSubscriptionRequest request) {
        return Objects.equals(subscription.getCustomerId(), request.customerId())
                && Objects.equals(subscription.getPlanName(), request.planName())
                && Objects.equals(subscription.getMonthlyAmount(), request.monthlyAmount())
                && Objects.equals(subscription.getActivationId(), request.activationId())
                && Objects.equals(subscription.getFulfillmentId(), request.fulfillmentId());
    }

    private Subscription conflict() {
        throw new ConflictException("A subscription already exists for this order with different details");
    }

    public List<Subscription> list() { return repository.findAll(); }

    public List<Subscription> byCustomer(String customerId, BillingStatus status) {
        String id = customerId.trim();
        return status == null ? repository.findByCustomerIdOrderByCreatedAtDesc(id)
                : repository.findByCustomerIdAndStatusOrderByCreatedAtDesc(id, status);
    }

    public Subscription byOrder(String orderId) {
        return repository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found for order " + orderId));
    }

    /**
     * Operator approval that starts recurring billing. Only allowed once payment, eSIM activation and
     * delivery are all complete; nextBillingAt is what the invoicing batch job picks up.
     */
    public Subscription approve(String orderId, String reason) {
        Subscription subscription = byOrder(orderId);
        if (subscription.getStatus() == BillingStatus.ACTIVE) return subscription;
        if (subscription.getStatus() != BillingStatus.READY_FOR_BILLING) {
            throw new ConflictException("Billing can only be approved when the subscription is READY_FOR_BILLING; it is "
                    + subscription.getStatus());
        }
        List<String> incomplete = new ArrayList<>();
        if (!"COMPLETED".equals(dependencies.payment(orderId).status())) incomplete.add("payment is not COMPLETED");
        DependencyClient.DependencyState fulfillment = dependencies.fulfillment(orderId);
        if (!matches(subscription.getFulfillmentId(), fulfillment) || !"DELIVERED".equals(fulfillment.status())) incomplete.add("delivery is not DELIVERED");
        DependencyClient.DependencyState activation = dependencies.activation(orderId);
        if (!matches(subscription.getActivationId(), activation) || !"ACTIVE".equals(activation.status())) incomplete.add("eSIM activation is not ACTIVE");
        if (!incomplete.isEmpty()) throw new ConflictException("Billing cannot start: " + String.join(", ", incomplete));

        Instant now = Instant.now();
        // Checkout paid the first month, so the first invoice is one calendar month after billing starts.
        LocalDate firstInvoice = now.atZone(ZoneOffset.UTC).toLocalDate().plusMonths(1);
        subscription.setStatus(BillingStatus.ACTIVE);
        subscription.setBillingStartedAt(now);
        subscription.setBillingDay(Math.min(firstInvoice.getDayOfMonth(), 28));
        subscription.setNextBillingAt(startOfDay(firstInvoice));
        return record(subscription, reason, now);
    }

    public Subscription reject(String orderId, String reason) {
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("A reason is required to reject billing");
        Subscription subscription = byOrder(orderId);
        if (subscription.getStatus() == BillingStatus.REJECTED) return subscription;
        if (!AWAITING_APPROVAL.contains(subscription.getStatus())) {
            throw new ConflictException("Billing can only be rejected before it starts; it is " + subscription.getStatus());
        }
        subscription.setStatus(BillingStatus.REJECTED);
        subscription.setNextBillingAt(null);
        return record(subscription, reason, Instant.now());
    }

    private Subscription record(Subscription subscription, String reason, Instant now) {
        subscription.setStatusReason(reason == null || reason.isBlank() ? null : reason.trim());
        subscription.setStatusChangedAt(now);
        return repository.save(subscription);
    }

    /** ACTIVE subscriptions whose next invoice falls on or before {@code asOf}, in a stable order for paging. */
    public List<Subscription> due(LocalDate asOf, int page, int size) {
        return repository.findByStatusAndNextBillingAtBefore(BillingStatus.ACTIVE, startOfDay(asOf.plusDays(1)),
                PageRequest.of(page, Math.min(Math.max(size, 1), 1000), Sort.by("id")));
    }

    /** Changes the invoicing day; the next invoice is prorated up to the new day by the invoicing batch. */
    public Subscription changeBillingDay(String orderId, int billingDay) {
        Subscription subscription = byOrder(orderId);
        if (subscription.getStatus() == BillingStatus.CANCELLED || subscription.getStatus() == BillingStatus.REJECTED) {
            throw new ConflictException("A " + subscription.getStatus() + " subscription has no billing day");
        }
        subscription.setBillingDay(billingDay);
        return repository.save(subscription);
    }

    /**
     * Records that [periodStart, periodEnd) has been invoiced by moving the next billing date to periodEnd. Idempotent: a
     * retry after success is a no-op, and a stale request (the period was already moved on) is rejected.
     */
    public Subscription recordBilledPeriod(String orderId, LocalDate periodStart, LocalDate periodEnd) {
        if (!periodEnd.isAfter(periodStart)) throw new IllegalArgumentException("periodEnd must be after periodStart");
        Subscription subscription = byOrder(orderId);
        LocalDate next = subscription.getNextBillingAt() == null ? null : subscription.getNextBillingAt().atZone(ZoneOffset.UTC).toLocalDate();
        if (periodEnd.equals(next)) return subscription;
        if (subscription.getStatus() != BillingStatus.ACTIVE) throw new ConflictException("Only ACTIVE subscriptions are billed; this one is " + subscription.getStatus());
        if (!periodStart.equals(next)) throw new ConflictException("Billing period " + periodStart + " does not match the next billing date " + next);
        subscription.setNextBillingAt(startOfDay(periodEnd));
        return repository.save(subscription);
    }

    private static Instant startOfDay(LocalDate date) { return date.atStartOfDay(ZoneOffset.UTC).toInstant(); }

    public Subscription suspend(String orderId) {
        Subscription subscription = byOrder(orderId);
        if (subscription.getStatus() == BillingStatus.CANCELLED) {
            throw new ConflictException("A cancelled subscription cannot be suspended");
        }
        if (subscription.getStatus() != BillingStatus.SUSPENDED) {
            subscription.setStatus(BillingStatus.SUSPENDED);
            return repository.save(subscription);
        }
        return subscription;
    }

    public Subscription cancel(String orderId) {
        Subscription subscription = byOrder(orderId);
        if (subscription.getStatus() != BillingStatus.CANCELLED) {
            subscription.setStatus(BillingStatus.CANCELLED);
            subscription.setNextBillingAt(null);
            return repository.save(subscription);
        }
        return subscription;
    }

    /** Tracks dependency progress up to READY_FOR_BILLING; starting billing is always an operator decision. */
    @Scheduled(fixedDelayString = "${billing.reconciliation-delay-ms:5000}")
    public void reconcile() {
        for (Subscription subscription : repository.findByStatusIn(AWAITING_APPROVAL)) {
            DependencyClient.DependencyState activation = dependencies.activation(subscription.getOrderId());
            DependencyClient.DependencyState fulfillment = dependencies.fulfillment(subscription.getOrderId());
            if (!matches(subscription.getActivationId(), activation)
                    || !matches(subscription.getFulfillmentId(), fulfillment)) {
                continue;
            }
            // Mirrors the order flow: the device is delivered before the eSIM is activated.
            BillingStatus next = !"DELIVERED".equals(fulfillment.status())
                    ? BillingStatus.WAITING_FOR_FULFILLMENT
                    : !"ACTIVE".equals(activation.status())
                            ? BillingStatus.WAITING_FOR_ACTIVATION
                            : BillingStatus.READY_FOR_BILLING;
            if (subscription.getStatus() != next) {
                subscription.setStatus(next);
                repository.save(subscription);
            }
        }
    }

    private boolean matches(String expectedId, DependencyClient.DependencyState actual) {
        return actual.id() == null || Objects.equals(expectedId, actual.id());
    }
}
