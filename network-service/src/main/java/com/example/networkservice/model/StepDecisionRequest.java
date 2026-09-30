package com.example.networkservice.model; import jakarta.validation.constraints.Size; public record StepDecisionRequest(@Size(max=500) String reason){}
