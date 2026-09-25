package com.example.orderservice.repository;
import com.example.orderservice.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;
public interface OrderRepository extends MongoRepository<Order,String>{List<Order> findByCustomerIdOrderByCreatedAtDesc(String customerId);}
