package com.example.customerservice.service;

import com.example.customerservice.exception.InvalidCredentialsException;
import com.example.customerservice.model.AuthResponse;
import com.example.customerservice.model.Customer;
import com.example.customerservice.model.LoginRequest;
import com.example.customerservice.model.RegisterRequest;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final CustomerService customers;
    private final PasswordEncoder passwords;
    private final JwtEncoder jwtEncoder;
    private final long ttlSeconds;

    public AuthService(CustomerService customers, PasswordEncoder passwords, JwtEncoder jwtEncoder,
                       @Value("${auth.jwt-ttl-seconds:3600}") long ttlSeconds) {
        this.customers = customers;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = ttlSeconds;
    }

    public AuthResponse register(RegisterRequest request) {
        Customer customer = new Customer(null, request.name().trim(), request.email().trim().toLowerCase(), request.phone(), true);
        customer.setPasswordHash(passwords.encode(request.password()));
        return response(customers.createCustomer(customer));
    }

    public AuthResponse login(LoginRequest request) {
        Customer customer;
        try {
            customer = customers.getCustomerByEmail(request.email().trim().toLowerCase());
        } catch (RuntimeException exception) {
            throw new InvalidCredentialsException();
        }
        if (!customer.isActive() || customer.getPasswordHash() == null || !passwords.matches(request.password(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return response(customer);
    }

    private AuthResponse response(Customer customer) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("customer-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(customer.getId())
                .claim("email", customer.getEmail())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", ttlSeconds, customer);
    }
}
