package dev.simplecore.searchable.autoconfigure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@AutoConfiguration
@Order(1)
public class SearchableJpaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SearchableJpaConfiguration.class);

    public SearchableJpaConfiguration() {
        log.info("SearchableJpaConfiguration is being initialized");
    }
} 