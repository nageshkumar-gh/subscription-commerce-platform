package com.example.orderservice.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;
    public WebConfig(@Value("${cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}") String allowedOrigins){
        this.allowedOrigins=java.util.Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value->!value.isBlank()).toArray(String[]::new);
    }
    public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET","POST","PATCH","DELETE","OPTIONS").allowedHeaders("*");}
}
