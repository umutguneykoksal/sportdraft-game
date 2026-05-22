package com.example.auction.integration;

import com.example.auction.AuctionApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Spins up ephemeral Postgres + Redis via Testcontainers (local and CI).
 * Spring starts containers once per test JVM; Flyway creates the {@code auction_it} schema;
 * containers are removed when the JVM exits.
 */
@SpringBootTest(classes = AuctionApplication.class)
@ImportTestcontainers
@ActiveProfiles(value = "it", inheritProfiles = false)
@EnabledIfDockerAvailable
abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("auction_it")
            .withUsername("auction")
            .withPassword("auction");

    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);
}
