package com.example.orchestration.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** The storefront's API: the signed-in customer's own orders and subscriptions. The customer is always the token's subject. */
@RestController
@RequestMapping("/api/me")
@Tag(name = "Customer orders")
public class CustomerOrdersController {
    public record PlaceOrderRequest(@NotBlank @Size(max = 100) String productId, @NotBlank @Size(max = 100) String planId) {}
    public record CancelRequest(@NotBlank @Size(max = 300) String reason) {}

    private final CustomerOrders orders;

    public CustomerOrdersController(CustomerOrders orders) { this.orders = orders; }

    @PostMapping("/orders")
    @Operation(summary = "Place an order for a phone and plan at catalogue prices")
    public ResponseEntity<Map<?, ?>> place(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PlaceOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orders.place(jwt.getSubject(), request.productId(), request.planId()));
    }

    @PostMapping("/orders/{orderId}/checkout")
    @Operation(summary = "Pay for and start the order")
    public ResponseEntity<Map<String, String>> checkout(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderId) {
        return ResponseEntity.accepted().body(Map.of("orderId", orderId, "status", orders.checkout(jwt.getSubject(), orderId)));
    }

    @GetMapping("/orders")
    @Operation(summary = "My orders, newest first")
    public List<Map<?, ?>> list(@AuthenticationPrincipal Jwt jwt) { return orders.list(jwt.getSubject()); }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "One of my orders with every step and its history")
    public Map<String, Object> details(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderId) { return orders.details(jwt.getSubject(), orderId); }

    @GetMapping("/orders/{orderId}/events")
    @Operation(summary = "Lifecycle history of one of my orders")
    public List<?> events(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderId) { return orders.events(jwt.getSubject(), orderId); }

    @PostMapping("/orders/{orderId}/cancel")
    @Operation(summary = "Cancel one of my orders before its subscription is active")
    public ResponseEntity<Map<String, String>> cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable String orderId, @Valid @RequestBody CancelRequest request) {
        return ResponseEntity.accepted().body(Map.of("orderId", orderId, "status", orders.cancel(jwt.getSubject(), orderId, request.reason())));
    }

    @GetMapping("/subscriptions")
    @Operation(summary = "My active subscriptions")
    public List<?> subscriptions(@AuthenticationPrincipal Jwt jwt) { return orders.activeSubscriptions(jwt.getSubject()); }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String, Object>> refused(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(Map.of("status", e.getStatusCode().value(), "message", String.valueOf(e.getReason())));
    }
}
