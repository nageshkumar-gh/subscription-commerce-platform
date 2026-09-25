package com.example.orderservice.service;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.assertEquals;
class OrderServiceTests {
    @Test void calculatesDeviceAndFirstMonthTotal(){assertEquals(new BigDecimal("928.99"),OrderService.calculateTotal(new BigDecimal("899.00"),new BigDecimal("29.99")));}
}
