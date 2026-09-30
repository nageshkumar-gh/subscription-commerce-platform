package com.example.billingservice.model;import jakarta.validation.constraints.*;
/** Day of the month the subscription is invoiced on; capped at 28 so every month has it. */
public record BillingDayRequest(@NotNull @Min(1) @Max(28) Integer billingDay){}
