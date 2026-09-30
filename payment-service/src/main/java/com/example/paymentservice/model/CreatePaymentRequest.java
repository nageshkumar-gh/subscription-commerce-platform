package com.example.paymentservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
public record CreatePaymentRequest(@NotBlank @Size(max=100) String orderId,@NotBlank @Size(max=100) String customerId,@NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal amount,@NotBlank @Pattern(regexp="[A-Za-z]{3}",message="currency must be a three-letter ISO code") String currency,@NotBlank @Size(max=50) String paymentMethod,@NotBlank @Size(max=512) String paymentToken) {}
