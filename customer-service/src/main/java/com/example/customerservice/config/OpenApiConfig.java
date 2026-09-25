package com.example.customerservice.config;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class OpenApiConfig {
    @Bean OpenAPI customerServiceOpenApi(){return new OpenAPI()
            .info(new Info().title("Customer Service API").version("2.0.0").description("Password registration, JWT login, and protected profile management."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));}
}
