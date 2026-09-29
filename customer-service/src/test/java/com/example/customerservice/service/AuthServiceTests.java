package com.example.customerservice.service;

import com.example.customerservice.exception.InvalidCredentialsException;
import com.example.customerservice.model.AuthResponse;
import com.example.customerservice.model.Customer;
import com.example.customerservice.model.LoginRequest;
import com.example.customerservice.model.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTests {
    @Mock
    private CustomerService customers;
    @Mock
    private PasswordEncoder passwords;
    @Mock
    private JwtEncoder jwtEncoder;

    private AuthService service;

    @BeforeEach
    void setUp() {
        service = new AuthService(customers, passwords, jwtEncoder, 3600);
    }

    @Test
    void registerHashesPasswordAndReturnsToken() {
        RegisterRequest request = new RegisterRequest(" Nagesh ", " NAGESH@Example.com ", "123456789", "Password1");
        when(passwords.encode("Password1")).thenReturn("bcrypt-hash");
        when(customers.createCustomer(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            customer.setId("customer-1");
            return customer;
        });
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt("access-token"));

        AuthResponse response = service.register(request);

        assertEquals("access-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600, response.expiresIn());
        assertEquals("nagesh@example.com", response.customer().getEmail());
        assertEquals("bcrypt-hash", response.customer().getPasswordHash());
        assertNotEquals("Password1", response.customer().getPasswordHash());
    }

    @Test
    void loginReturnsTokenForValidCredentials() {
        Customer customer = new Customer("customer-1", "Nagesh", "nagesh@example.com", "123456789", true);
        customer.setPasswordHash("bcrypt-hash");
        when(customers.getCustomerByEmail("nagesh@example.com")).thenReturn(customer);
        when(passwords.matches("Password1", "bcrypt-hash")).thenReturn(true);
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(jwt("access-token"));

        AuthResponse response = service.login(new LoginRequest(" NAGESH@Example.com ", "Password1"));

        assertEquals("access-token", response.accessToken());
    }

    @Test
    void loginUsesGenericFailureForWrongPassword() {
        Customer customer = new Customer("customer-1", "Nagesh", "nagesh@example.com", "123456789", true);
        customer.setPasswordHash("bcrypt-hash");
        when(customers.getCustomerByEmail("nagesh@example.com")).thenReturn(customer);
        when(passwords.matches("wrong", "bcrypt-hash")).thenReturn(false);

        InvalidCredentialsException exception = assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("nagesh@example.com", "wrong"))
        );

        assertEquals("Email or password is incorrect", exception.getMessage());
    }

    @Test
    void disabledCustomerCannotLogin() {
        Customer customer = new Customer("customer-1", "Nagesh", "nagesh@example.com", "123456789", false);
        customer.setPasswordHash("bcrypt-hash");
        when(customers.getCustomerByEmail("nagesh@example.com")).thenReturn(customer);

        assertThrows(
                InvalidCredentialsException.class,
                () -> service.login(new LoginRequest("nagesh@example.com", "Password1"))
        );
    }

    private Jwt jwt(String token) {
        return Jwt.withTokenValue(token)
                .header("alg", "HS256")
                .subject("customer-1")
                .build();
    }
}
