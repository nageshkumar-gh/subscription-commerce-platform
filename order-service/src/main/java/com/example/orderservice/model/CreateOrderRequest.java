package com.example.orderservice.model;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
public record CreateOrderRequest(
    @NotBlank String customerId,
    @NotBlank String productId,
    @NotBlank String productName,
    @NotBlank String storage,
    @NotBlank String planId,
    @NotBlank String planName,
    @NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal devicePrice,
    @NotNull @DecimalMin(value="0.0",inclusive=false) BigDecimal monthlyPrice
) {}
