package dev.simplecore.searchable.test.config;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.FixedLocaleResolver;

import java.util.Locale;

@Configuration
@EnableAutoConfiguration
@EntityScan(basePackages = "dev.simplecore.searchable.test.entity")
@EnableJpaRepositories(basePackages = "dev.simplecore.searchable.test.repository")
@ComponentScan(basePackages = "dev.simplecore.searchable")
public class BaseTestConfig {
    
    @Bean
    public LocaleResolver localeResolver() {
        return new FixedLocaleResolver(Locale.ENGLISH);
    }
} 