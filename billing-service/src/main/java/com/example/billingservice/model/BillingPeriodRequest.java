package com.example.billingservice.model;import jakarta.validation.constraints.NotNull;import java.time.LocalDate;
/** A billing period that has been invoiced: [periodStart, periodEnd). */
public record BillingPeriodRequest(@NotNull LocalDate periodStart,@NotNull LocalDate periodEnd){}
