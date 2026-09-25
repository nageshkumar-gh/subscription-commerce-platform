package com.example.customerservice.service;

import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer createCustomer(Customer customer) {
        if (customerRepository.existsByEmail(customer.getEmail())) {
            throw new IllegalArgumentException(
                    "Customer with this email already exists"
            );
        }

        return customerRepository.save(customer);
    }

    public Customer getCustomerByEmail(String email) {
        return customerRepository.findByEmail(email.toLowerCase())
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

    public Customer updateCustomer(String id, Customer updatedCustomer) {
        Customer existingCustomer = getCustomerById(id);

        if (!existingCustomer.getEmail().equals(updatedCustomer.getEmail())
                && customerRepository.existsByEmail(updatedCustomer.getEmail())) {
            throw new IllegalArgumentException(
                    "Customer with this email already exists"
            );
        }

        existingCustomer.setName(updatedCustomer.getName());
        existingCustomer.setEmail(updatedCustomer.getEmail());
        existingCustomer.setPhone(updatedCustomer.getPhone());
        existingCustomer.setActive(updatedCustomer.isActive());

        return customerRepository.save(existingCustomer);
    }

    public void deleteCustomer(String id) {
        Customer customer = getCustomerById(id);
        customerRepository.delete(customer);
    }
}
