package com.example.auction.integration;

import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

final class IntegrationContainers {

    private static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine");

    private static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forListeningPort());

    private IntegrationContainers() {
    }

    static final class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext context) {
            startIfNeeded();
            TestPropertyValues.of(
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.data.redis.host=" + REDIS.getHost(),
                "spring.data.redis.port=" + REDIS.getMappedPort(6379)
            ).applyTo(context.getEnvironment());
        }
    }

    private static void startIfNeeded() {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
        }
        if (!REDIS.isRunning()) {
            REDIS.start();
        }
    }
}
