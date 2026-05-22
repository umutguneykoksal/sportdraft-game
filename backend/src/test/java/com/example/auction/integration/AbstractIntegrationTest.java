package com.example.auction.integration;

import com.example.auction.AuctionApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(classes = AuctionApplication.class)
@Import(IntegrationTestContainersConfig.class)
@ActiveProfiles(value = "it", inheritProfiles = false)
@EnabledIfDockerAvailable
abstract class AbstractIntegrationTest {
}
