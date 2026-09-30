package com.example.orchestration;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.orchestration.config.SecurityConfig;
import com.example.orchestration.customer.CustomerOrders;
import com.example.orchestration.customer.CustomerOrdersController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CustomerOrdersController.class, properties = "auth.jwt-secret=test-secret-that-is-at-least-32-bytes-long")
@Import(SecurityConfig.class)
class CustomerApiSecurityTests {
    @Autowired MockMvc mvc;
    @MockitoBean CustomerOrders orders;

    @Test
    void customerApiRequiresAToken() throws Exception {
        mvc.perform(get("/api/me/orders")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/me/orders/order-1/cancel").contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"x\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(orders);
    }

    @Test
    void actsOnlyForTheTokenSubject() throws Exception {
        when(orders.list("customer-1")).thenReturn(List.of(Map.of("id", "order-1")));
        mvc.perform(get("/api/me/orders").with(jwt().jwt(token -> token.subject("customer-1")))).andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value("order-1"));
        mvc.perform(post("/api/me/orders").with(jwt().jwt(token -> token.subject("customer-1"))).contentType(MediaType.APPLICATION_JSON)
            .content("{\"productId\":\"p1\",\"planId\":\"plan-1\",\"customerId\":\"someone-else\",\"devicePrice\":1}")).andExpect(status().isCreated());
        verify(orders).place("customer-1", "p1", "plan-1");
    }
}
