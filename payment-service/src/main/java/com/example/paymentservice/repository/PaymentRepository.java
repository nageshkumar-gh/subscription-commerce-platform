package com.example.paymentservice.repository;
import com.example.paymentservice.model.Payment;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
import java.util.Optional;
public interface PaymentRepository extends MongoRepository<Payment,String>{boolean existsByOrderId(String orderId);Optional<Payment> findByOrderId(String orderId);List<Payment> findByCustomerIdOrderByCreatedAtDesc(String customerId);}
