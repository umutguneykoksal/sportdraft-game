package com.example.auction.integration;

import com.example.auction.AuctionApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest(classes = AuctionApplication.class)
@ContextConfiguration(initializers = IntegrationContainers.Initializer.class)
@ActiveProfiles(value = "it", inheritProfiles = false)
@EnabledIfDockerAvailable
abstract class AbstractIntegrationTest {
}
