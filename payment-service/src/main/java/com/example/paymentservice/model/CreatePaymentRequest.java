package com.example.paymentservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record CreatePaymentRequest(@NotBlank String orderId,@NotBlank String customerId,@NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal amount,@NotBlank String currency,@NotBlank String paymentMethod,@NotBlank String paymentToken) {}
