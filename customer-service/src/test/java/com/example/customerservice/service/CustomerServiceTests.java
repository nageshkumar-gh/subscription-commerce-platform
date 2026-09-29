package com.example.customerservice.service;

import com.example.customerservice.model.Customer;
import com.example.customerservice.model.UpdateCustomerRequest;
import com.example.customerservice.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTests {
    @Mock
    private CustomerRepository repository;

    private CustomerService service;

    @BeforeEach
    void setUp() {
        service = new CustomerService(repository);
    }

    @Test
    void createNormalizesCustomerDetails() {
        Customer customer = new Customer(null, " Nagesh ", " NAGESH@Example.com ", " 123456789 ", true);
        when(repository.save(customer)).thenReturn(customer);

        Customer saved = service.createCustomer(customer);

        assertEquals("Nagesh", saved.getName());
        assertEquals("nagesh@example.com", saved.getEmail());
        assertEquals("123456789", saved.getPhone());
        verify(repository).existsByEmail("nagesh@example.com");
    }

    @Test
    void createRejectsDuplicateNormalizedEmail() {
        Customer customer = new Customer(null, "Nagesh", " NAGESH@Example.com ", "123456789", true);
        when(repository.existsByEmail("nagesh@example.com")).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> service.createCustomer(customer));
        verify(repository, never()).save(customer);
    }

    @Test
    void updateNormalizesFieldsAndCannotChangeActivationState() {
        Customer existing = new Customer("customer-1", "Old", "old@example.com", "1234567", false);
        when(repository.findById("customer-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        Customer saved = service.updateCustomer(
                "customer-1",
                new UpdateCustomerRequest(" New Name ", " NEW@Example.com ", " 7654321 ")
        );

        assertEquals("New Name", saved.getName());
        assertEquals("new@example.com", saved.getEmail());
        assertEquals("7654321", saved.getPhone());
        assertEquals(false, saved.isActive());
    }
}
