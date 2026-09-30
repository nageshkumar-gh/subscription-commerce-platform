package com.example.paymentservice.repository;
import com.example.paymentservice.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
public interface PaymentRepository extends MongoRepository<Payment,String>{/** The order's checkout payment; monthly charges carry an invoiceId. */Optional<Payment> findFirstByOrderIdAndInvoiceIdIsNull(String orderId);Optional<Payment> findByInvoiceId(String invoiceId);Optional<Payment> findByIdempotencyKey(String idempotencyKey);List<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId);}
