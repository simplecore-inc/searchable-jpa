package dev.simplecore.searchable.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

@Data
@Validated
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {
    private SwaggerProperties swagger = new SwaggerProperties();

    @Valid
    private HibernateProperties hibernate = new HibernateProperties();

    @Valid
    private DateTimeProperties dateTime = new DateTimeProperties();

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
    }

    @Data
    public static class DateTimeProperties {
        /**
         * Application timezone (IANA id, e.g. "Asia/Seoul", "UTC") used to interpret
         * timezone-less search values and expand date-only BETWEEN boundaries.
         * When null, resolution falls back to an application ZoneId bean, then
         * spring.jackson.time-zone, then UTC.
         */
        private String defaultTimezone;
    }

    @Data
    public static class HibernateProperties {
        /**
         * Enable automatic Hibernate optimization configuration.
         * When enabled, the library will automatically configure optimal settings for N+1 prevention.
         */
        private boolean autoOptimization = true;
        
        /**
         * Default batch fetch size for lazy loading.
         * This helps prevent N+1 problems by fetching related entities in batches.
         */
        @Min(1)
        private int defaultBatchFetchSize = 100;

        /**
         * JDBC batch size for bulk operations.
         */
        @Min(1)
        private int jdbcBatchSize = 1000;
        
        /**
         * Enable batch versioned data for optimistic locking.
         */
        private boolean batchVersionedData = true;
        
        /**
         * Enable order inserts optimization.
         */
        private boolean orderInserts = true;
        
        /**
         * Enable order updates optimization.
         */
        private boolean orderUpdates = true;
        
        /**
         * Enable IN clause parameter padding for better query plan caching.
         */
        private boolean inClauseParameterPadding = true;
    }
} 