package com.example.customerservice.service;

import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.model.UpdateCustomerRequest;
import com.example.customerservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer createCustomer(Customer customer) {
        customer.setName(customer.getName().trim());
        customer.setEmail(normalizeEmail(customer.getEmail()));
        customer.setPhone(customer.getPhone().trim());
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException(
                    "Customer with this email already exists"
            );
        }

        return customerRepository.save(customer);
    }

    public Customer getCustomerByEmail(String email) {
        return customerRepository.findByEmail(normalizeEmail(email))
                .orElseThrow(() -> new CustomerNotFoundException("Email or password is incorrect"));
    }

    public List<Customer> getAllCustomers() {
        return customerRepository.findAll();
    }

    public Customer getCustomerById(String id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Customer not found with ID: " + id
                ));
    }

    public Customer updateCustomer(String id, UpdateCustomerRequest updatedCustomer) {
        Customer existingCustomer = getCustomerById(id);
        String normalizedEmail = normalizeEmail(updatedCustomer.email());

        if (!existingCustomer.getEmail().equals(normalizedEmail)
                && customerRepository.existsByEmail(normalizedEmail)) {
            throw new IllegalArgumentException(
                    "Customer with this email already exists"
            );
        }

        existingCustomer.setName(updatedCustomer.name().trim());
        existingCustomer.setEmail(normalizedEmail);
        existingCustomer.setPhone(updatedCustomer.phone().trim());

        return customerRepository.save(existingCustomer);
    }

    public void deleteCustomer(String id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
