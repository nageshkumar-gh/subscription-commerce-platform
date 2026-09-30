package com.example.billingservice.model;import jakarta.validation.constraints.Size;public record BillingDecisionRequest(@Size(max=500) String reason){}
