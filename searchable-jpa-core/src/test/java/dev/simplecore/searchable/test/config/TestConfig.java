package dev.simplecore.searchable.test.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

@Configuration
@EnableAutoConfiguration
@EntityScan(basePackages = "dev.simplecore.searchable.test.entity")
@EnableJpaRepositories(basePackages = "dev.simplecore.searchable.test.repository")
@ComponentScan(basePackages = "dev.simplecore.searchable.test")
public class TestConfig {
    @PersistenceContext
    private EntityManager entityManager;

    @Bean
    public EntityManager entityManager() {
        return entityManager;
    }
} 