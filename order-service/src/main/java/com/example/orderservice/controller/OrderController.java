package com.example.orderservice.controller;
import com.example.orderservice.model.CreateOrderRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.UpdateOrderStatusRequest;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
@RestController @RequestMapping("/api/orders")
@Tag(name="Orders",description="Create and manage phone subscription orders")
public class OrderController {
    private final OrderService service;
    public OrderController(OrderService service){this.service=service;}
    @PostMapping @Operation(summary="Create an order",description="Creates an order in PENDING_PAYMENT and calculates the device plus first-month total.") public ResponseEntity<Order> create(@Valid @RequestBody CreateOrderRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));}
    @GetMapping @Operation(summary="List orders",description="Optionally filter orders by customerId.") public List<Order> list(@RequestParam(required=false) String customerId){return service.list(customerId);}
    @GetMapping("/{id}") @Operation(summary="Get an order by ID") public Order get(@PathVariable String id){return service.get(id);}
    @PatchMapping("/{id}/status") @Operation(summary="Update order status") public Order updateStatus(@PathVariable String id,@Valid @RequestBody UpdateOrderStatusRequest request){return service.updateStatus(id,request.status());}
    @DeleteMapping("/{id}") @Operation(summary="Delete an order") public ResponseEntity<Void> delete(@PathVariable String id){service.delete(id);return ResponseEntity.noContent().build();}
}
