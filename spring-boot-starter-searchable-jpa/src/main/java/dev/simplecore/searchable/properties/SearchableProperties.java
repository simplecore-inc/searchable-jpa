package dev.simplecore.searchable.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {
    private SwaggerProperties swagger = new SwaggerProperties();
    private HibernateProperties hibernate = new HibernateProperties();

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
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
        private int defaultBatchFetchSize = 100;
        
        /**
         * JDBC batch size for bulk operations.
         */
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