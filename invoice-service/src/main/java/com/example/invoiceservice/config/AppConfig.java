package com.example.invoiceservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.batch.autoconfigure.BatchTaskExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AppConfig implements WebMvcConfigurer {
    private final String[] origins;

    public AppConfig(@Value("${cors.allowed-origins}") String origins) {
        this.origins = Arrays.stream(origins.split(",")).map(String::trim).filter(value -> !value.isBlank()).toArray(String[]::new);
    }

    /** Runs execute in the background so "Run now" returns immediately with the run id; tests run them synchronously. */
    @Bean
    @BatchTaskExecutor
    TaskExecutor invoicingTaskExecutor(@Value("${invoice.async-runs:true}") boolean async) {
        if (!async) return new SyncTaskExecutor();
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("invoicing-");
        executor.setConcurrencyLimit(1);
        return executor;
    }

    @Bean
    OpenAPI api() {
        return new OpenAPI().info(new Info().title("Invoicing API").version("1.0.0")
            .description("Daily Spring Batch run that invoices and charges due monthly subscriptions."));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET", "POST", "PUT", "OPTIONS");
    }
}
