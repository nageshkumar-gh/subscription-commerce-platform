package com.example.productservice.config;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
@Configuration
public class WebConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;
    public WebConfig(@Value("${cors.allowed-origins}") String allowedOrigins){this.allowedOrigins=Arrays.stream(allowedOrigins.split(",")).map(String::trim).filter(value->!value.isEmpty()).toArray(String[]::new);}
    public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOrigins(allowedOrigins).allowedMethods("GET","POST","PUT","DELETE","OPTIONS").allowedHeaders("*");}
}
