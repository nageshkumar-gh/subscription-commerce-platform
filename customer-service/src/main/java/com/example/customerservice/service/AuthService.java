package com.example.customerservice.service;

import com.example.customerservice.exception.InvalidCredentialsException;
import com.example.customerservice.exception.CustomerNotFoundException;
import com.example.customerservice.model.AuthResponse;
import com.example.customerservice.model.Customer;
import com.example.customerservice.model.LoginRequest;
import com.example.customerservice.model.RegisterRequest;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
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
    private final Set<String> catalogAdminCustomerIds;

    public AuthService(CustomerService customers, PasswordEncoder passwords, JwtEncoder jwtEncoder,
                       @Value("${auth.jwt-ttl-seconds:3600}") long ttlSeconds,
                       @Value("${auth.catalog-admin-customer-ids:}") String catalogAdminCustomerIds) {
        this.customers = customers;
        this.passwords = passwords;
        this.jwtEncoder = jwtEncoder;
        this.ttlSeconds = ttlSeconds;
        this.catalogAdminCustomerIds = Arrays.stream(catalogAdminCustomerIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public AuthResponse register(RegisterRequest request) {
        Customer customer = new Customer(null, request.name().trim(), request.email().trim().toLowerCase(Locale.ROOT), request.phone().trim(), true);
        customer.setPasswordHash(passwords.encode(request.password()));
        // Registration is deliberately customer-only. Administrative scope is only
        // evaluated on a later authenticated login for a pre-provisioned customer ID.
        return response(customers.createCustomer(customer), false);
    }

    public AuthResponse login(LoginRequest request) {
        Customer customer;
        try {
            customer = customers.getCustomerByEmail(request.email().trim().toLowerCase(Locale.ROOT));
        } catch (CustomerNotFoundException exception) {
            throw new InvalidCredentialsException();
        }
        if (!customer.isActive() || customer.getPasswordHash() == null || !passwords.matches(request.password(), customer.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return response(customer, true);
    }

    private AuthResponse response(Customer customer, boolean allowConfiguredAdminScope) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("customer-service")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .subject(customer.getId())
                .claim("email", customer.getEmail());
        if (allowConfiguredAdminScope && catalogAdminCustomerIds.contains(customer.getId())) {
            claims.claim("scope", "catalog:write");
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
        return new AuthResponse(token, "Bearer", ttlSeconds, customer);
    }
}
