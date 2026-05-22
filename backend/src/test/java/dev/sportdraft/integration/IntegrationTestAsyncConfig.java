package dev.sportdraft.integration;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@Profile("it")
@EnableAsync
class IntegrationTestAsyncConfig implements AsyncConfigurer {

    private final SyncTaskExecutor taskExecutor = new SyncTaskExecutor();

    @Bean
    IntegrationTestStateCleaner integrationTestStateCleaner(
        StringRedisTemplate redis,
        JdbcTemplate jdbc
    ) {
        return new IntegrationTestStateCleaner(redis, jdbc);
    }

    @Override
    public Executor getAsyncExecutor() {
        return taskExecutor;
    }
}
