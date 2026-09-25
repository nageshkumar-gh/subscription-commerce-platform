package com.example.customerservice.controller;

import com.example.customerservice.model.Customer;
import com.example.customerservice.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/customers")
@Tag(name = "Customers", description = "Register and manage customers")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated customer's profile")
    public ResponseEntity<Customer> getCurrentCustomer(JwtAuthenticationToken authentication) {
        return ResponseEntity.ok(customerService.getCustomerById(authentication.getName()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated customer's profile")
    public ResponseEntity<Customer> updateCurrentCustomer(
            JwtAuthenticationToken authentication,
            @Valid @RequestBody Customer customer) {
        return ResponseEntity.ok(customerService.updateCustomer(authentication.getName(), customer));
    }

    @DeleteMapping("/me")
    @Operation(summary = "Delete the authenticated customer's profile")
    public ResponseEntity<Void> deleteCurrentCustomer(JwtAuthenticationToken authentication) {
        customerService.deleteCustomer(authentication.getName());
        return ResponseEntity.noContent().build();
    }
}
