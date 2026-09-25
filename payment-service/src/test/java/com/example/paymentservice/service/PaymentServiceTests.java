package com.example.paymentservice.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
class PaymentServiceTests{@Test void transactionReferenceHasSafePrefix(){assertTrue(PaymentService.createTransactionReference().matches("PAY-[A-F0-9]{8}"));}}
