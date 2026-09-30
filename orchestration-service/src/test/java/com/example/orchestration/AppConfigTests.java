package com.example.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.orchestration.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

class AppConfigTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withPropertyValues("cors.allowed-origins=http://localhost:5174")
        .withUserConfiguration(AppConfig.class);

    @Test
    void providesRestClientBuilder() {
        contextRunner.run(context -> assertThat(context).hasSingleBean(RestClient.Builder.class));
    }
}
