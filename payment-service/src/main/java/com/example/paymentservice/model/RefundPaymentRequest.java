package com.example.paymentservice.model;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record RefundPaymentRequest(@NotBlank @Size(max=500) String reason) {}
