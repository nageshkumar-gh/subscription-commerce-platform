package com.example.orchestration.workflow;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Order saga driven by operator decisions in the admin UI:
 * payment -> device delivery -> eSIM activation -> billing.
 * The eSIM is only activated once the customer has the phone, so service never runs on an undelivered device.
 * Each stage is polled until an operator approves or rejects it; a rejection fails the order with the reason.
 */
public class OrderLifecycleWorkflowImpl implements OrderLifecycleWorkflow {
    // Poll quickly while an operator is likely watching, then back off to keep workflow history bounded.
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);
    private static final Duration SLOW_POLL_INTERVAL = Duration.ofMinutes(5);
    private static final Duration FAST_POLL_WINDOW = Duration.ofMinutes(15);
    // Dependency outages (e.g. a service redeploy) are retried for up to 10 minutes instead of failing the order.
    private final OrderActivities activities = Workflow.newActivityStub(OrderActivities.class,
        ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(20)).setScheduleToCloseTimeout(Duration.ofMinutes(10))
            .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1)).setBackoffCoefficient(2)
                .setMaximumInterval(Duration.ofSeconds(30))
                .setDoNotRetry("DEPENDENCY_REJECTED", "INVALID_DEPENDENCY_RESPONSE").build()).build());
    private String state = "STARTED";
    private int eventSequence;
    private String cancelReason;
    /** False for workflows started before order statuses were synced, so their history still replays. */
    private boolean syncOrderStatus;
    /** Step statuses that move the order record forward: paid, dispatched, delivered, eSIM active, billing active. */
    private static final Map<String, String> ORDER_STATUS = Map.of("PAYMENT:COMPLETED", "PAID", "FULFILLMENT:DISPATCHED", "DISPATCHED",
        "FULFILLMENT:DELIVERED", "DELIVERED", "ESIM_ACTIVATION:ACTIVE", "ACTIVATED", "BILLING:ACTIVE", "COMPLETED");

    /** Thrown inside the workflow when an operator cancels; never leaves {@link #run}. */
    private static final class CancelRequested extends RuntimeException {}

    public String run(OrderWorkflowRequest r) {
        syncOrderStatus=Workflow.getVersion("order-status-sync",Workflow.DEFAULT_VERSION,1)>=1;
        try {
            emit(r,"ORDER_WORKFLOW","STARTED",null);
            state="WAITING_FOR_PAYMENT";
            emit(r,"ORDER_WORKFLOW","WAITING_FOR_PAYMENT",null);
            String payment=activities.createPayment(r); emit(r,"PAYMENT",payment,"Payment intent created; awaiting operator approval");
            await(r,"PAYMENT","Payment",payment,()->activities.paymentStatus(r.orderId()),"COMPLETED",Duration.ofHours(24),"FAILED","REFUNDED");

            checkCancelled(); state="AWAITING_DELIVERY"; emit(r,"ORDER_WORKFLOW",state,null);
            String fulfillmentId=activities.fulfill(r); emit(r,"FULFILLMENT","REQUESTED",null);
            await(r,"FULFILLMENT","Delivery","REQUESTED",()->activities.fulfillmentStatus(r.orderId()),"DELIVERED",Duration.ofDays(7),"FAILED");

            checkCancelled(); state="AWAITING_ACTIVATION"; emit(r,"ORDER_WORKFLOW",state,null);
            String activationId=activities.activate(r); emit(r,"ESIM_ACTIVATION","REQUESTED",null);
            await(r,"ESIM_ACTIVATION","eSIM activation","REQUESTED",()->activities.activationStatus(r.orderId()),"ACTIVE",Duration.ofDays(3),"FAILED");

            checkCancelled(); state="AWAITING_BILLING_APPROVAL"; emit(r,"ORDER_WORKFLOW",state,null);
            activities.startBilling(r,activationId,fulfillmentId);
            await(r,"BILLING","Billing",null,()->activities.billingStatus(r.orderId()),"ACTIVE",Duration.ofDays(7),"REJECTED");

            state="COMPLETED"; emit(r,"ORDER_WORKFLOW","COMPLETED",null); return state;
        } catch(CancelRequested cancelled) {
            state="CANCELLING"; emit(r,"ORDER_WORKFLOW","CANCELLING",cancelReason);
            String outcome=activities.compensate(r,cancelReason);
            state="CANCELLED"; emit(r,"ORDER_WORKFLOW","CANCELLED",cancelReason+(outcome.isBlank()?"":" ("+outcome+")")); return state;
        } catch(RuntimeException failure) {
            state="FAILED"; emit(r,"ORDER_WORKFLOW","FAILED",failure.getMessage()); syncOrder(r,"FAILED"); throw failure;
        }
    }
    /** Polls one step, publishing each change from {@code published}, until it reaches {@code done}, a failure status, or the deadline. */
    private void await(OrderWorkflowRequest r,String step,String label,String published,Supplier<String> status,String done,Duration timeout,String... failed) {
        long start=Workflow.currentTimeMillis(), deadline=start+timeout.toMillis();
        String last=published;
        while(Workflow.currentTimeMillis()<deadline){
            String s=status.get();
            if(Arrays.asList(failed).contains(s))throw rejected(r,step,s,label);
            if(!Objects.equals(s,last)){emit(r,step,s,null);last=s;String order=ORDER_STATUS.get(step+":"+s);if(order!=null)syncOrder(r,order);}
            if(done.equals(s))return;
            // Wakes early when an operator cancels, so cancellation does not wait for the next poll.
            Workflow.await(pollInterval(start),()->cancelReason!=null);
            checkCancelled();
        }
        throw ApplicationFailure.newNonRetryableFailure(label+" did not complete within "+timeout.toHours()+" hours",step+"_TIMEOUT");
    }
    /** Records the step failure with the operator's reason and returns the failure that ends the saga. */
    private ApplicationFailure rejected(OrderWorkflowRequest r,String step,String status,String label) {
        String reason=activities.rejectionReason(step,r.orderId());
        String message=label+" ended in "+status+(reason==null||reason.isBlank()?"":": "+reason);
        emit(r,step,status,reason);
        return ApplicationFailure.newNonRetryableFailure(message,step+"_"+status);
    }
    private Duration pollInterval(long waitStartedAt) {
        return Workflow.currentTimeMillis()-waitStartedAt<FAST_POLL_WINDOW.toMillis()?POLL_INTERVAL:SLOW_POLL_INTERVAL;
    }
    private void emit(OrderWorkflowRequest r,String type,String status,String detail){String id="order-"+r.orderId()+"-"+(++eventSequence)+"-"+type+"-"+status;activities.publish(r,id,type,status,detail);}
    private void syncOrder(OrderWorkflowRequest r,String status){if(syncOrderStatus)activities.syncOrderStatus(r.orderId(),status);}
    private void checkCancelled(){if(cancelReason!=null)throw new CancelRequested();}
    public String status(){return state;}
    public void cancel(String reason){if(cancelReason==null&&!"COMPLETED".equals(state)&&!"FAILED".equals(state))cancelReason=reason==null||reason.isBlank()?"Cancelled by operator":reason.trim();}
}
