package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.core.utils.SearchableTimeZoneHolder;
import dev.simplecore.searchable.properties.SearchableProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

@Configuration
@AutoConfiguration(before = HibernateJpaAutoConfiguration.class)
@ConditionalOnClass(EntityManager.class)
@EnableConfigurationProperties(SearchableProperties.class)
@Order(1)
public class SearchableJpaConfiguration {
    private static final Logger log = LoggerFactory.getLogger(SearchableJpaConfiguration.class);

    /**
     * Optional bean name a host application may define to publish its canonical
     * application timezone as a single {@link ZoneId} value.
     */
    private static final String APPLICATION_ZONE_ID_BEAN = "applicationZoneId";

    private final SearchableProperties searchableProperties;
    private final ConfigurableEnvironment environment;
    private final BeanFactory beanFactory;

    public SearchableJpaConfiguration(SearchableProperties searchableProperties,
                                      ConfigurableEnvironment environment,
                                      BeanFactory beanFactory) {
        this.searchableProperties = searchableProperties;
        this.environment = environment;
        this.beanFactory = beanFactory;
        log.trace("SearchableJpaConfiguration is being initialized");
    }

    // Note: @Conditional-style annotations have no effect on @PostConstruct, so this runs unconditionally.
    @PostConstruct
    public void configureSearchableTimeZone() {
        ZoneId zone = resolveApplicationZoneId();
        SearchableTimeZoneHolder.setZoneId(zone);
        log.info("Searchable value parser timezone set to: {}", zone);
    }

    private ZoneId resolveApplicationZoneId() {
        // 1. Explicit searchable override
        String configured = searchableProperties.getDateTime().getDefaultTimezone();
        if (configured != null && !configured.isEmpty()) {
            try {
                return ZoneId.of(configured);
            } catch (Exception e) {
                log.warn("Invalid searchable.date-time.default-timezone '{}', falling back", configured);
            }
        }
        // 2. Host-provided application ZoneId bean (single canonical value), when the host defines one
        if (beanFactory.containsBean(APPLICATION_ZONE_ID_BEAN)
                && beanFactory.isTypeMatch(APPLICATION_ZONE_ID_BEAN, ZoneId.class)) {
            return beanFactory.getBean(APPLICATION_ZONE_ID_BEAN, ZoneId.class);
        }
        // 3. Spring Jackson standard key (shared, framework-neutral)
        String springTimezone = environment.getProperty("spring.jackson.time-zone");
        if (springTimezone != null && !springTimezone.isEmpty()) {
            try {
                return ZoneId.of(springTimezone);
            } catch (Exception e) {
                log.warn("Invalid spring.jackson.time-zone '{}', falling back to UTC", springTimezone);
            }
        }
        // 4. Deployment-independent default (never the JVM default timezone)
        return ZoneOffset.UTC;
    }

    // Note: @Conditional-style annotations are only evaluated for @Bean/@Configuration processing and
    // have no effect on a @PostConstruct method, so the auto-optimization gate is the manual check below.
    @PostConstruct
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

        // Register as the lowest-priority source so these are defaults only: any user configuration
        // (application.yml/properties, system properties, environment) overrides them.
        propertySources.addLast(searchableHibernateProperties);
        
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