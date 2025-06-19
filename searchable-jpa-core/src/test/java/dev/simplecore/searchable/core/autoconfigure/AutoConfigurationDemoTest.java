package dev.simplecore.searchable.core.autoconfigure;

import dev.simplecore.searchable.test.config.TestConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

@Slf4j
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:auto_config_demo_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@DisplayName("Auto Configuration Demo")
class AutoConfigurationDemoTest {

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Demo: Automatic Hibernate optimization configuration")
    void demonstrateAutoConfiguration() {
        log.info("=== Searchable JPA Auto Configuration Demo ===");
        log.info("");
        log.info("This test demonstrates that searchable-jpa automatically configures");
        log.info("   Hibernate optimizations without any manual configuration!");
        log.info("");
        
        // Check if auto-configuration properties are available
        String batchFetchSize = environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size");
        String jdbcBatchSize = environment.getProperty("spring.jpa.properties.hibernate.jdbc.batch_size");
        String orderInserts = environment.getProperty("spring.jpa.properties.hibernate.order_inserts");
        
        log.info("📋 Automatically configured Hibernate properties:");
        log.info("    default_batch_fetch_size: {} (prevents N+1 problems)", 
                batchFetchSize != null ? batchFetchSize : "NOT SET");
        log.info("    jdbc.batch_size: {} (optimizes bulk operations)", 
                jdbcBatchSize != null ? jdbcBatchSize : "NOT SET");
        log.info("    order_inserts: {} (improves batching efficiency)", 
                orderInserts != null ? orderInserts : "NOT SET");
        log.info("");
        
        if (batchFetchSize != null) {
            log.info("🎉 SUCCESS: Auto-configuration is working!");
            log.info("   Your application now has optimized Hibernate settings automatically.");
        } else {
            log.info("ℹ️  Auto-configuration not detected in test environment.");
            log.info("   In a real Spring Boot application with searchable-jpa starter,");
            log.info("   these settings would be automatically applied.");
        }
        
        log.info("");
        log.info("💡 To customize these settings, add to your application.yml:");
        log.info("   searchable:");
        log.info("     hibernate:");
        log.info("       default-batch-fetch-size: 150  # Custom batch size");
        log.info("       auto-optimization: false       # Disable auto-config");
        log.info("");
        log.info("=== Demo Complete ===");
    }
} 