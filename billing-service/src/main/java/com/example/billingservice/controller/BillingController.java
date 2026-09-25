package com.example.billingservice.controller;
import com.example.billingservice.model.*;
import com.example.billingservice.service.BillingService;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/subscriptions") @Tag(name="Billing")public class BillingController{private final BillingService service;public BillingController(BillingService s){service=s;}@PostMapping @Operation(summary="Create subscription awaiting dependencies")public ResponseEntity<Subscription>create(@Valid@RequestBody CreateSubscriptionRequest q){return ResponseEntity.status(201).body(service.create(q));}@GetMapping @Operation(summary="List subscriptions or find by order")public Object list(@RequestParam(required=false)String orderId){return orderId==null?service.list():service.byOrder(orderId);}}
