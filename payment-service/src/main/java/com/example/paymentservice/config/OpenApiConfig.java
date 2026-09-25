package com.example.paymentservice.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig{@Bean OpenAPI paymentServiceOpenApi(){return new OpenAPI().info(new Info().title("Payment Service API").version("1.0.0").description("Simulated payments for subscription orders. No real card data is stored or processed."));}}
