package com.example.paymentservice.service;

import com.example.paymentservice.model.CreatePaymentRequest;
import com.example.paymentservice.model.Payment;
import com.example.paymentservice.model.PaymentStatus;
import com.example.paymentservice.exception.PaymentConflictException;
import com.example.paymentservice.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentServiceTests {
    @Test void transactionReferenceHasSafePrefix(){assertTrue(PaymentService.createTransactionReference().matches("PAY-[A-F0-9]{8}"));}
    @Test void normalizesIdempotencyKey(){assertEquals("key-1",PaymentService.normalizeIdempotencyKey(" key-1 "));}
    @Test void rejectsMissingIdempotencyKey(){assertThrows(IllegalArgumentException.class,()->PaymentService.normalizeIdempotencyKey(" "));}
    @Test void recognizesIdenticalIdempotentRequest(){CreatePaymentRequest request=request("order-1");assertTrue(PaymentService.sameRequest(paymentFrom(request,"key-1"),request));}
    @Test void recognizesChangedIdempotentRequest(){CreatePaymentRequest original=request("order-1");assertFalse(PaymentService.sameRequest(paymentFrom(original,"key-1"),request("order-2")));}
    @Test void approveCompletesPendingPaymentWithReason(){PaymentRepository repository=repositoryWith(pending());Payment approved=new PaymentService(repository).approve("pay-1"," card verified ");assertEquals(PaymentStatus.COMPLETED,approved.getStatus());assertEquals("card verified",approved.getStatusReason());}
    @Test void rejectFailsPendingPaymentWithReason(){PaymentRepository repository=repositoryWith(pending());Payment rejected=new PaymentService(repository).reject("pay-1","Suspected fraud");assertEquals(PaymentStatus.FAILED,rejected.getStatus());assertEquals("Suspected fraud",rejected.getStatusReason());}
    @Test void rejectRequiresReason(){PaymentRepository repository=repositoryWith(pending());assertThrows(IllegalArgumentException.class,()->new PaymentService(repository).reject("pay-1"," "));verify(repository,never()).save(any());}
    @Test void decisionIsIdempotentButCannotBeReversed(){Payment payment=pending();payment.setStatus(PaymentStatus.COMPLETED);PaymentRepository repository=repositoryWith(payment);PaymentService service=new PaymentService(repository);assertSame(payment,service.approve("pay-1",null));assertThrows(PaymentConflictException.class,()->service.reject("pay-1","too late"));verify(repository,never()).save(any());}
    @Test void recurringChargeCompletesAndIsIdempotentPerInvoice(){PaymentRepository repository=mock(PaymentRepository.class);when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());when(repository.findByInvoiceId("INV-1")).thenReturn(Optional.empty());when(repository.save(any())).thenAnswer(i->i.getArgument(0));PaymentService service=new PaymentService(repository,"");var result=service.charge(charge("customer-1"),"invoice-INV-1");assertTrue(result.created());assertEquals(PaymentStatus.COMPLETED,result.payment().getStatus());assertEquals("INV-1",result.payment().getInvoiceId());when(repository.findByInvoiceId("INV-1")).thenReturn(Optional.of(result.payment()));var again=service.charge(charge("customer-1"),"another-key");assertFalse(again.created());verify(repository,times(1)).save(any());}
    @Test void recurringChargeCanBeSimulatedAsDeclined(){PaymentRepository repository=mock(PaymentRepository.class);when(repository.findByIdempotencyKey(any())).thenReturn(Optional.empty());when(repository.findByInvoiceId(any())).thenReturn(Optional.empty());when(repository.save(any())).thenAnswer(i->i.getArgument(0));Payment payment=new PaymentService(repository," customer-9 ,x").charge(charge("customer-9"),"k").payment();assertEquals(PaymentStatus.FAILED,payment.getStatus());assertEquals("Card declined (simulated)",payment.getStatusReason());}
    private static com.example.paymentservice.model.RecurringChargeRequest charge(String customer){return new com.example.paymentservice.model.RecurringChargeRequest("INV-1","order-1",customer,new BigDecimal("29.99"),"eur");}
    private static Payment pending(){Payment payment=paymentFrom(request("order-1"),"key-1");payment.setId("pay-1");return payment;}
    private static PaymentRepository repositoryWith(Payment payment){PaymentRepository repository=mock(PaymentRepository.class);when(repository.findById("pay-1")).thenReturn(Optional.of(payment));when(repository.save(any())).thenAnswer(i->i.getArgument(0));return repository;}
    private static CreatePaymentRequest request(String orderId){return new CreatePaymentRequest(orderId,"customer-1",new BigDecimal("29.99"),"eur","card","provider-token-not-persisted");}
    private static Payment paymentFrom(CreatePaymentRequest request,String key){Payment payment=new Payment();payment.setOrderId(request.orderId());payment.setCustomerId(request.customerId());payment.setAmount(request.amount());payment.setCurrency(request.currency().toUpperCase());payment.setPaymentMethod(request.paymentMethod());payment.setIdempotencyKey(key);payment.setStatus(PaymentStatus.PENDING);return payment;}
}
