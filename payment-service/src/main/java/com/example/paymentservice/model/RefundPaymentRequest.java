package com.example.paymentservice.model;
import jakarta.validation.constraints.NotBlank;
public record RefundPaymentRequest(@NotBlank String reason) {}
