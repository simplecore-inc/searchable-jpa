package dev.simplecore.searchable.autoconfigure;

import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.ConfigurableEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies Category 8 (Spring Boot starter) issues 8-1 through 8-3.
 */
class SearchableJpaConfigurationTest {

    private final EntityManager entityManager = mock(EntityManager.class);

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(EntityManager.class, () -> entityManager)
            .withConfiguration(AutoConfigurations.of(SearchableJpaConfiguration.class));

    // ---- baseline: defaults are applied when auto-optimization is on ----

    @Test
    @DisplayName("auto-optimization registers the Hibernate default property source")
    void autoOptimizationRegistersDefaults() {
        runner.run(context -> {
            ConfigurableEnvironment environment = (ConfigurableEnvironment) context.getEnvironment();
            assertThat(environment.getPropertySources().contains("searchableHibernateOptimizations")).isTrue();
            assertThat(environment.getProperty("spring.jpa.properties.hibernate.default_batch_fetch_size"))
                    .isEqualTo("100");
        });
    }

    // ---- 8-1: the manual gate actually disables optimization ----

    @Test
    @DisplayName("8-1: setting auto-optimization=false disables the optimization via the manual check")
    void autoOptimizationCanBeDisabled() {
        runner.withPropertyValues("searchable.hibernate.auto-optimization=false").run(context -> {
            ConfigurableEnvironment environment = (ConfigurableEnvironment) context.getEnvironment();
            assertThat(environment.getPropertySources().contains("searchableHibernateOptimizations")).isFalse();
        });
    }

    // ---- 8-2: user configuration overrides the library defaults ----

    @Test
    @DisplayName("8-2: a user-provided Hibernate property overrides the library default")
    void userConfigurationOverridesLibraryDefault() {
        runner.withPropertyValues("spring.jpa.properties.hibernate.jdbc.batch_size=500").run(context -> {
            assertThat(context.getEnvironment().getProperty("spring.jpa.properties.hibernate.jdbc.batch_size"))
                    .isEqualTo("500");
        });
    }

    // ---- 8-3: invalid batch sizes fail fast at startup ----

    @Test
    @DisplayName("8-3: a non-positive jdbc batch size fails validation at startup")
    void invalidJdbcBatchSizeFailsFast() {
        runner.withPropertyValues("searchable.hibernate.jdbc-batch-size=0").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("jdbcBatchSize");
        });
    }

    @Test
    @DisplayName("8-3: a non-positive default batch fetch size fails validation at startup")
    void invalidDefaultBatchFetchSizeFailsFast() {
        runner.withPropertyValues("searchable.hibernate.default-batch-fetch-size=-1").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("defaultBatchFetchSize");
        });
    }

    // ---- time bucket counting ----

    @Test
    @DisplayName("the starter registers a TimeBucketCounter bean")
    void registersTimeBucketCounter() {
        runner.run(context -> assertThat(context).hasSingleBean(TimeBucketCounter.class));
    }

    @Test
    @DisplayName("a TimeBucketCounter defined by the application replaces the auto-registered one")
    void applicationDefinedTimeBucketCounterWins() {
        TimeBucketCounter custom = new TimeBucketCounter(entityManager);

        runner.withBean("customTimeBucketCounter", TimeBucketCounter.class, () -> custom)
                .run(context -> assertThat(context).getBean(TimeBucketCounter.class).isSameAs(custom));
    }
}
