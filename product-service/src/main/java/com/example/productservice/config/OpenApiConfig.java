package com.example.productservice.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI productServiceOpenApi(){return new OpenAPI().info(new Info().title("Product Service API").version("1.0.0").description("Phone catalogue and eSIM plans."));}
}
