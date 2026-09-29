package com.example.customerservice.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.Locale;

public record UpdateCustomerRequest(
        @NotBlank(message = "Customer name is required") String name,
        @NotBlank(message = "Email is required") @Email(message = "Email format is invalid") String email,
        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{7,15}$", message = "Phone number must contain 7 to 15 digits") String phone
) {
    public UpdateCustomerRequest {
        name = trim(name);
        email = email == null ? null : email.trim().toLowerCase(Locale.ROOT);
        phone = trim(phone);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
