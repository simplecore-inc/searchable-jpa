package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.properties.SearchableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;

@Configuration
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManager.class)
@EnableConfigurationProperties(SearchableProperties.class)
@Order(1)
public class SearchableJpaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SearchableJpaConfiguration.class);
    
    private final SearchableProperties searchableProperties;
    private final ConfigurableEnvironment environment;

    public SearchableJpaConfiguration(SearchableProperties searchableProperties, ConfigurableEnvironment environment) {
        this.searchableProperties = searchableProperties;
        this.environment = environment;
        log.trace("SearchableJpaConfiguration is being initialized");
    }

    @PostConstruct
    @ConditionalOnProperty(name = "searchable.hibernate.auto-optimization", havingValue = "true", matchIfMissing = true)
    public void configureHibernateOptimizations() {
        if (!searchableProperties.getHibernate().isAutoOptimization()) {
            log.info("Searchable Hibernate auto-optimization is disabled");
            return;
        }

        log.trace("Configuring automatic Hibernate optimizations for searchable-jpa...");
        
        SearchableProperties.HibernateProperties hibernateProps = searchableProperties.getHibernate();
        
        Map<String, Object> hibernateOptimizations = new HashMap<>();
        
        // N+1 problem prevention
        hibernateOptimizations.put("spring.jpa.properties.hibernate.default_batch_fetch_size", 
                String.valueOf(hibernateProps.getDefaultBatchFetchSize()));
        
        // Batch processing optimizations
        hibernateOptimizations.put("spring.jpa.properties.hibernate.jdbc.batch_size", 
                String.valueOf(hibernateProps.getJdbcBatchSize()));
        hibernateOptimizations.put("spring.jpa.properties.hibernate.jdbc.batch_versioned_data", 
                String.valueOf(hibernateProps.isBatchVersionedData()));
        
        // Insert/Update ordering for better batching
        hibernateOptimizations.put("spring.jpa.properties.hibernate.order_inserts", 
                String.valueOf(hibernateProps.isOrderInserts()));
        hibernateOptimizations.put("spring.jpa.properties.hibernate.order_updates", 
                String.valueOf(hibernateProps.isOrderUpdates()));
        
        // Query optimization
        hibernateOptimizations.put("spring.jpa.properties.hibernate.query.in_clause_parameter_padding", 
                String.valueOf(hibernateProps.isInClauseParameterPadding()));
        
        // Connection optimization
        hibernateOptimizations.put("spring.jpa.properties.hibernate.connection.provider_disables_autocommit", "true");
        
        // Add optimizations to environment
        MutablePropertySources propertySources = environment.getPropertySources();
        MapPropertySource searchableHibernateProperties = new MapPropertySource(
                "searchableHibernateOptimizations", hibernateOptimizations);
        
        // Add with high priority (but after command line arguments)
        propertySources.addAfter("systemProperties", searchableHibernateProperties);
        
        log.trace("Applied Hibernate optimizations:");
        log.trace("  - default_batch_fetch_size: {}", hibernateProps.getDefaultBatchFetchSize());
        log.trace("  - jdbc.batch_size: {}", hibernateProps.getJdbcBatchSize());
        log.trace("  - order_inserts: {}", hibernateProps.isOrderInserts());
        log.trace("  - order_updates: {}", hibernateProps.isOrderUpdates());
        log.trace("  - in_clause_parameter_padding: {}", hibernateProps.isInClauseParameterPadding());
        log.trace("These settings help prevent N+1 problems and improve performance automatically.");
        log.trace("To disable auto-optimization, set: searchable.hibernate.auto-optimization=false");
    }
} 