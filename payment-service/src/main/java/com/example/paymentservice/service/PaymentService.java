package com.example.paymentservice.service;
import com.example.paymentservice.exception.PaymentNotFoundException;
import com.example.paymentservice.model.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
@Service
public class PaymentService {
    private final PaymentRepository repository;
    public PaymentService(PaymentRepository repository){this.repository=repository;}
    public Payment create(CreatePaymentRequest request){
        if(repository.existsByOrderId(request.orderId()))throw new IllegalStateException("A payment already exists for this order");
        Payment payment=new Payment();payment.setOrderId(request.orderId());payment.setCustomerId(request.customerId());payment.setAmount(request.amount());payment.setCurrency(request.currency().toUpperCase());payment.setPaymentMethod(request.paymentMethod());
        payment.setTransactionReference(createTransactionReference());payment.setStatus("fail".equalsIgnoreCase(request.paymentToken())?PaymentStatus.FAILED:PaymentStatus.COMPLETED);
        Instant now=Instant.now();payment.setCreatedAt(now);payment.setUpdatedAt(now);return repository.save(payment);
    }
    public Payment get(String id){return repository.findById(id).orElseThrow(()->new PaymentNotFoundException("Payment not found with ID: "+id));}
    public Payment getByOrderId(String orderId){return repository.findByOrderId(orderId).orElseThrow(()->new PaymentNotFoundException("Payment not found for order: "+orderId));}
    public List<Payment> listByCustomer(String customerId){return repository.findByCustomerIdOrderByCreatedAtDesc(customerId);}
    public Payment refund(String id){Payment payment=get(id);if(payment.getStatus()!=PaymentStatus.COMPLETED)throw new IllegalStateException("Only completed payments can be refunded");payment.setStatus(PaymentStatus.REFUNDED);payment.setUpdatedAt(Instant.now());return repository.save(payment);}
    public static String createTransactionReference(){return "PAY-"+UUID.randomUUID().toString().substring(0,8).toUpperCase();}
}
