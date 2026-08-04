package com.techpulse.techradar.config;

import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.r2dbc.connection.R2dbcTransactionManager;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

/**
 * R2DBC PostgreSQL configuration for reactive database access.
 */
@Configuration
@EnableR2dbcRepositories(basePackages = "com.techpulse.techradar.features")
@RequiredArgsConstructor
public class PostgresConfig {

    @Bean
    public DatabaseClient databaseClient(ConnectionFactory connectionFactory) {
        return DatabaseClient.create(connectionFactory);
    }

    @Bean
    public ReactiveTransactionManager transactionManager(ConnectionFactory connectionFactory) {
        return new R2dbcTransactionManager(connectionFactory);
    }

    /**
     * Lets a reactive chain wrap multiple statements in one transaction via
     * {@code .as(transactionalOperator::transactional)} — first real use is the transactional
     * outbox (see {@code shared.outbox}), which needs the {@code tech_analytics} upsert and the
     * {@code outbox_event} insert to commit or roll back together.
     */
    @Bean
    public TransactionalOperator transactionalOperator(ReactiveTransactionManager transactionManager) {
        return TransactionalOperator.create(transactionManager);
    }
}
