package com.example.orchestration.config;
import io.swagger.v3.oas.models.OpenAPI;import io.swagger.v3.oas.models.info.Info;import org.apache.kafka.clients.admin.NewTopic;import org.springframework.beans.factory.annotation.Value;import org.springframework.context.annotation.*;import org.springframework.kafka.config.TopicBuilder;import org.springframework.web.client.RestClient;import org.springframework.web.servlet.config.annotation.*;
@Configuration public class AppConfig implements WebMvcConfigurer{
 private final String[] origins; public AppConfig(@Value("${cors.allowed-origins}")String origins){this.origins=origins.split(",");}
 @Bean RestClient.Builder restClientBuilder(){return RestClient.builder();}
 @Bean NewTopic events(){return TopicBuilder.name("order-lifecycle-events").partitions(3).replicas(1).build();}
 @Bean OpenAPI api(){return new OpenAPI().info(new Info().title("Order Orchestration API").version("1.0.0").description("Temporal-managed order saga with Kafka lifecycle events."));}
 public void addCorsMappings(CorsRegistry r){r.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST","OPTIONS");}
}
