package com.example.customerservice.repository;

import com.example.customerservice.model.Customer;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface CustomerRepository
        extends MongoRepository<Customer, String> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);
}