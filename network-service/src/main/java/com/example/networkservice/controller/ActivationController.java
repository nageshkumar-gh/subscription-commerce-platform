package com.example.networkservice.controller;

import com.example.networkservice.model.*;
import com.example.networkservice.service.ActivationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/activations")
@Tag(name = "eSIM activation")
public class ActivationController {
    private final ActivationService service;
    public ActivationController(ActivationService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "Create or return an existing activation for an order")
    public ResponseEntity<Activation> create(@Valid @RequestBody CreateActivationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Approve the current step", description = "Advances QUEUED to ACTIVATING, or ACTIVATING to ACTIVE.")
    public Activation approve(@PathVariable String id, @Valid @RequestBody(required = false) StepDecisionRequest request) {
        return service.approve(id, request == null ? null : request.reason());
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Reject the activation", description = "Moves a QUEUED or ACTIVATING activation to FAILED. A reason is required.")
    public Activation reject(@PathVariable String id, @Valid @RequestBody StepDecisionRequest request) {
        return service.reject(id, request.reason());
    }

    @GetMapping
    @Operation(summary = "List activations or find one by order")
    public Object list(@RequestParam(required = false) String orderId) {
        return orderId == null ? service.list() : service.byOrder(orderId);
    }
}
