package com.example.paymentservice.controller;
import com.example.paymentservice.model.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.RefundPaymentRequest;
import com.example.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/payments") @Tag(name="Payments",description="Simulate and inspect order payments")
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service){this.service=service;}
    @PostMapping @Operation(summary="Create a simulated payment",description="Stores no card number. Any token completes payment except the test token 'fail'.") public ResponseEntity<Payment> create(@Valid @RequestBody CreatePaymentRequest request){return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));}
    @GetMapping("/{id}") @Operation(summary="Get a payment by ID") public Payment get(@PathVariable String id){return service.get(id);}
    @GetMapping(params="orderId") @Operation(summary="Get the payment for an order") public Payment getByOrder(@RequestParam String orderId){return service.getByOrderId(orderId);}
    @GetMapping(params="customerId") @Operation(summary="List payments for a customer") public List<Payment> listByCustomer(@RequestParam String customerId){return service.listByCustomer(customerId);}
    @PostMapping("/{id}/refund") @Operation(summary="Refund a completed payment") public Payment refund(@PathVariable String id,@Valid @RequestBody RefundPaymentRequest request){return service.refund(id);}
}
