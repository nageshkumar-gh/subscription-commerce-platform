package com.example.paymentservice.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig{@Bean OpenAPI paymentServiceOpenApi(){return new OpenAPI().info(new Info().title("Payment Service API").version("1.0.0").description("Idempotent payment intents for subscription orders. Success requires external provider confirmation; raw payment credentials are never persisted."));}}
