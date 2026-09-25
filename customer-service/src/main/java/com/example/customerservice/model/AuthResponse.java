package com.example.customerservice.model;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Customer customer
) {}
