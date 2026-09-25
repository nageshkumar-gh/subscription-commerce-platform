package com.example.orderservice.model;
import jakarta.validation.constraints.NotNull;
public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {}
