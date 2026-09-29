package com.example.customerservice.controller;

import com.example.customerservice.model.Customer;
import com.example.customerservice.repository.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.data.mongodb.auto-index-creation=false",
        "app.cors.allowed-origins=https://customers.example.com,http://54.226.253.13:5173"
})
@AutoConfigureMockMvc
class CustomerApiIntegrationTests {
    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private CustomerRepository repository;

    private final Map<String, Customer> customersById = new HashMap<>();
    private final Map<String, Customer> customersByEmail = new HashMap<>();
    private final AtomicInteger ids = new AtomicInteger();

    @BeforeEach
    void setUpRepository() {
        reset(repository);
        customersById.clear();
        customersByEmail.clear();
        ids.set(0);

        when(repository.existsByEmail(anyString())).thenAnswer(invocation ->
                customersByEmail.containsKey(invocation.getArgument(0)));
        when(repository.findByEmail(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(customersByEmail.get(invocation.getArgument(0))));
        when(repository.findById(anyString())).thenAnswer(invocation ->
                Optional.ofNullable(customersById.get(invocation.getArgument(0))));
        when(repository.save(any(Customer.class))).thenAnswer(invocation -> {
            Customer customer = invocation.getArgument(0);
            if (customer.getId() == null) {
                customer.setId("customer-" + ids.incrementAndGet());
            }
            customersById.put(customer.getId(), customer);
            customersByEmail.put(customer.getEmail(), customer);
            return customer;
        });
    }

    @Test
    void registrationLoginAndProtectedProfileFlow() throws Exception {
        String registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Nagesh ","email":" NAGESH@Example.com ","phone":"123456789","password":"Password1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.customer.name").value("Nagesh"))
                .andExpect(jsonPath("$.customer.email").value("nagesh@example.com"))
                .andExpect(jsonPath("$.customer.active").value(true))
                .andExpect(jsonPath("$.customer.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String registrationToken = objectMapper.readTree(registration).get("accessToken").asText();
        mvc.perform(get("/api/customers/me").header("Authorization", "Bearer " + registrationToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("customer-1"));

        String login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" NAGESH@EXAMPLE.COM ","password":"Password1"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        String loginToken = objectMapper.readTree(login).get("accessToken").asText();

        mvc.perform(put("/api/customers/me")
                        .header("Authorization", "Bearer " + loginToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Updated Name ","email":" UPDATED@Example.com ","phone":"7654321","active":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void protectedProfileRequiresJwt() throws Exception {
        mvc.perform(get("/api/customers/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsPreflightAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/auth/register")
                        .header("Origin", "http://54.226.253.13:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://54.226.253.13:5173"));
    }

    @Test
    void corsPreflightRejectsUnconfiguredOrigin() throws Exception {
        mvc.perform(options("/api/auth/register")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }

    @Test
    void duplicateEmailReturnsStructuredConflictForPrecheckAndDatabaseRace() throws Exception {
        customer("customer-existing", "existing@example.com", true);

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Duplicate","email":" EXISTING@Example.com ","phone":"1234567","password":"Password1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Customer with this email already exists"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));

        customersByEmail.clear();
        when(repository.save(any(Customer.class))).thenThrow(new DuplicateKeyException("email_1"));
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Race","email":"race@example.com","phone":"1234567","password":"Password1"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Customer with this email already exists"));
    }

    @Test
    void invalidCredentialsAndDisabledCustomersReturnGenericUnauthorizedError() throws Exception {
        customer("customer-active", "active@example.com", true);
        customer("customer-disabled", "disabled@example.com", false);

        assertUnauthorizedLogin("active@example.com", "WrongPassword");
        assertUnauthorizedLogin("missing@example.com", "Password1");
        assertUnauthorizedLogin("disabled@example.com", "Password1");
    }

    private void assertUnauthorizedLogin(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Email or password is incorrect"))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));
    }

    private void customer(String id, String email, boolean active) {
        Customer customer = new Customer(id, "Customer", email, "1234567", active);
        customer.setPasswordHash(passwordEncoder.encode("Password1"));
        customersById.put(id, customer);
        customersByEmail.put(email, customer);
    }
}
