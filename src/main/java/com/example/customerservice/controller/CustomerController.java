package com.example.customerservice.controller;

import com.example.customerservice.model.Customer;
import com.example.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(
            @Valid @RequestBody Customer customer
    ) {
        Customer createdCustomer =
                customerService.createCustomer(customer);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(createdCustomer);
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(
                customerService.getAllCustomers()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(
            @PathVariable String id
    ) {
        return ResponseEntity.ok(
                customerService.getCustomerById(id)
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(
            @PathVariable String id,
            @Valid @RequestBody Customer customer
    ) {
        return ResponseEntity.ok(
                customerService.updateCustomer(id, customer)
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(
            @PathVariable String id
    ) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }
}