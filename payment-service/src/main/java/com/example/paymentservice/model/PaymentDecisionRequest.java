package com.example.paymentservice.model;
import jakarta.validation.constraints.Size;
public record PaymentDecisionRequest(@Size(max=500) String reason) {}
