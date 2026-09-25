package com.example.customerservice.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Customer name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits") String phone,
        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 72, message = "Password must contain 8 to 72 characters") String password
) {}
