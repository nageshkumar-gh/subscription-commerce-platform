package com.example.paymentservice.service;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.exception.PaymentConflictException;
import com.example.paymentservice.model.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.model.RecurringChargeRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service
public class PaymentService {
    private final PaymentRepository repository;
    private final Set<String> declinedCustomers;
    public PaymentService(PaymentRepository repository){this(repository,"");}
    @Autowired public PaymentService(PaymentRepository repository,@Value("${payment.simulated-decline-customers:}") String declinedCustomers){this.repository=repository;this.declinedCustomers=Arrays.stream(declinedCustomers.split(",")).map(String::trim).filter(v->!v.isEmpty()).collect(Collectors.toSet());}
    public CreateResult create(CreatePaymentRequest request,String idempotencyKey){
        String normalizedKey=normalizeIdempotencyKey(idempotencyKey);
        var existingByKey=repository.findByIdempotencyKey(normalizedKey);
        if(existingByKey.isPresent()){
            Payment existing=existingByKey.get();
            if(!sameRequest(existing,request))throw new PaymentConflictException("Idempotency key was already used for a different payment request");
            return new CreateResult(existing,false);
        }
        if(repository.findFirstByOrderIdAndInvoiceIdIsNull(request.orderId()).isPresent())throw new PaymentConflictException("A payment already exists for this order");
        Payment payment=new Payment();payment.setOrderId(request.orderId());payment.setCustomerId(request.customerId());payment.setAmount(request.amount());payment.setCurrency(request.currency().toUpperCase());payment.setPaymentMethod(request.paymentMethod());
        payment.setIdempotencyKey(normalizedKey);payment.setTransactionReference(createTransactionReference());payment.setStatus(PaymentStatus.PENDING);
        Instant now=Instant.now();payment.setCreatedAt(now);payment.setUpdatedAt(now);
        try{return new CreateResult(repository.save(payment),true);}catch(DuplicateKeyException exception){Payment concurrent=repository.findByIdempotencyKey(normalizedKey).orElseThrow(()->new PaymentConflictException("A payment already exists for this order"));if(!sameRequest(concurrent,request))throw new PaymentConflictException("Idempotency key was already used for a different payment request");return new CreateResult(concurrent,false);}
    }
    public Payment get(String id){return repository.findById(id).orElseThrow(()->new PaymentNotFoundException("Payment not found with ID: "+id));}
    public Payment getByOrderId(String orderId){return repository.findFirstByOrderIdAndInvoiceIdIsNull(orderId).orElseThrow(()->new PaymentNotFoundException("Payment not found for order: "+orderId));}
    public List<Payment> listByCustomer(String customerId){return repository.findByCustomerIdOrderByCreatedAtDesc(customerId);}
    /**
     * Charges one invoice. Simulated provider: succeeds unless the customer is configured to be declined. Idempotent on
     * both the Idempotency-Key and the invoice, so a batch retry never charges twice.
     */
    public CreateResult charge(RecurringChargeRequest request,String idempotencyKey){
        String key=normalizeIdempotencyKey(idempotencyKey);
        var existing=repository.findByIdempotencyKey(key).or(()->repository.findByInvoiceId(request.invoiceId()));
        if(existing.isPresent()){if(!request.invoiceId().equals(existing.get().getInvoiceId()))throw new PaymentConflictException("Idempotency key was already used for a different payment");return new CreateResult(existing.get(),false);}
        Payment payment=new Payment();payment.setOrderId(request.orderId());payment.setInvoiceId(request.invoiceId());payment.setCustomerId(request.customerId());payment.setAmount(request.amount());payment.setCurrency(request.currency().toUpperCase());payment.setPaymentMethod("SIMULATED_RECURRING");payment.setIdempotencyKey(key);payment.setTransactionReference(createTransactionReference());
        boolean declined=declinedCustomers.contains(request.customerId());
        payment.setStatus(declined?PaymentStatus.FAILED:PaymentStatus.COMPLETED);payment.setStatusReason(declined?"Card declined (simulated)":"Monthly charge for invoice "+request.invoiceId());
        Instant now=Instant.now();payment.setCreatedAt(now);payment.setUpdatedAt(now);
        try{return new CreateResult(repository.save(payment),true);}catch(DuplicateKeyException race){return new CreateResult(repository.findByInvoiceId(request.invoiceId()).orElseThrow(()->race),false);}
    }
    public Payment getByInvoiceId(String invoiceId){return repository.findByInvoiceId(invoiceId).orElseThrow(()->new PaymentNotFoundException("Payment not found for invoice: "+invoiceId));}
    public List<Payment> list(){return repository.findAll(Sort.by(Sort.Direction.DESC,"createdAt"));}
    public Payment approve(String id,String reason){return decide(id,PaymentStatus.COMPLETED,reason);}
    public Payment reject(String id,String reason){if(reason==null||reason.isBlank())throw new IllegalArgumentException("A reason is required to reject a payment");return decide(id,PaymentStatus.FAILED,reason);}
    private Payment decide(String id,PaymentStatus outcome,String reason){Payment payment=get(id);if(payment.getStatus()==outcome)return payment;if(payment.getStatus()!=PaymentStatus.PENDING)throw new PaymentConflictException("Only pending payments can be approved or rejected; this payment is "+payment.getStatus());payment.setStatus(outcome);payment.setStatusReason(reason==null||reason.isBlank()?null:reason.trim());payment.setUpdatedAt(Instant.now());return repository.save(payment);}
    public Payment refund(String id,String reason){Payment payment=get(id);if(payment.getStatus()!=PaymentStatus.COMPLETED)throw new PaymentConflictException("Only completed payments can be refunded");payment.setStatus(PaymentStatus.REFUND_PENDING);payment.setRefundReason(reason.trim());payment.setUpdatedAt(Instant.now());return repository.save(payment);}
    static String normalizeIdempotencyKey(String key){if(key==null||key.isBlank())throw new IllegalArgumentException("Idempotency-Key header is required");String normalized=key.trim();if(normalized.length()>128)throw new IllegalArgumentException("Idempotency-Key must not exceed 128 characters");return normalized;}
    static boolean sameRequest(Payment payment,CreatePaymentRequest request){return payment.getOrderId().equals(request.orderId())&&payment.getCustomerId().equals(request.customerId())&&payment.getAmount().compareTo(request.amount())==0&&payment.getCurrency().equalsIgnoreCase(request.currency())&&payment.getPaymentMethod().equals(request.paymentMethod());}
    public static String createTransactionReference(){return "PAY-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}
    public record CreateResult(Payment payment,boolean created) {}
}
