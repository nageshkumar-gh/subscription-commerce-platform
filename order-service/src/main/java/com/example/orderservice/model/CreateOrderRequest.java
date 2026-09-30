package com.example.orderservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
public record CreateOrderRequest(
    @NotBlank @Size(max=100) String customerId,
    @NotBlank @Size(max=100) String productId,
    @NotBlank @Size(max=200) String productName,
    @NotBlank @Size(max=50) String storage,
    @NotBlank @Size(max=100) String planId,
    @NotBlank @Size(max=200) String planName,
    @NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal devicePrice,
    @NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal monthlyPrice
) {}
