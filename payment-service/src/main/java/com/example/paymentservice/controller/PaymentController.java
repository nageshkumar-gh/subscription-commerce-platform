package com.example.paymentservice.controller;
import com.example.paymentservice.model.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentDecisionRequest;
import com.example.paymentservice.model.RecurringChargeRequest;
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
    @PostMapping @Operation(summary="Create a payment intent",description="Creates a pending, idempotent payment intent. It never stores the supplied token or reports success before a payment provider confirms it.") public ResponseEntity<Payment> create(@RequestHeader("Idempotency-Key") String idempotencyKey,@Valid @RequestBody CreatePaymentRequest request){PaymentService.CreateResult result=service.create(request,idempotencyKey);return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.payment());}
    @GetMapping("/{id}") @Operation(summary="Get a payment by ID") public Payment get(@PathVariable String id){return service.get(id);}
    @GetMapping(params="orderId") @Operation(summary="Get the payment for an order") public Payment getByOrder(@RequestParam String orderId){return service.getByOrderId(orderId);}
    @GetMapping(params="customerId") @Operation(summary="List payments for a customer") public List<Payment> listByCustomer(@RequestParam String customerId){return service.listByCustomer(customerId);}
    @PostMapping("/charges") @Operation(summary="Charge a monthly invoice",description="Simulated recurring charge against the customer's stored payment method; completes or fails immediately. Idempotent per invoice.") public ResponseEntity<Payment> charge(@RequestHeader("Idempotency-Key") String idempotencyKey,@Valid @RequestBody RecurringChargeRequest request){PaymentService.CreateResult result=service.charge(request,idempotencyKey);return ResponseEntity.status(result.created()?HttpStatus.CREATED:HttpStatus.OK).body(result.payment());}
    @GetMapping(params="invoiceId") @Operation(summary="Get the payment for an invoice") public Payment getByInvoice(@RequestParam String invoiceId){return service.getByInvoiceId(invoiceId);}
    @GetMapping @Operation(summary="List all payments, newest first") public List<Payment> list(){return service.list();}
    @PostMapping("/{id}/approve") @Operation(summary="Approve a pending payment",description="Operator confirmation that stands in for a payment provider; moves PENDING to COMPLETED.") public Payment approve(@PathVariable String id,@Valid @RequestBody(required=false) PaymentDecisionRequest request){return service.approve(id,request==null?null:request.reason());}
    @PostMapping("/{id}/reject") @Operation(summary="Reject a pending payment",description="Moves PENDING to FAILED. A reason is required.") public Payment reject(@PathVariable String id,@Valid @RequestBody PaymentDecisionRequest request){return service.reject(id,request.reason());}
    @PostMapping("/{id}/refund") @Operation(summary="Request a refund",description="Moves a completed payment to REFUND_PENDING; a provider integration must later confirm REFUNDED.") public Payment refund(@PathVariable String id,@Valid @RequestBody RefundPaymentRequest request){return service.refund(id,request.reason());}
}
