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
            assertThat(environment.getProperty("spring.jpa.properties.hibernate.connection.provider_disables_autocommit")).isNull();
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
    @Test
    void limitsApplyEvenWhenOptimizationIsDisabled() {
        runner.withPropertyValues("searchable.hibernate.auto-optimization=false",
                "searchable.default-page-size=50", "searchable.max-page-size=2000",
                "searchable.hibernate.auto-fetch-collections=false").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.jpa.properties.searchable.default_page_size")).isEqualTo("50");
            assertThat(context.getEnvironment().getProperty("spring.jpa.properties.searchable.max_page_size")).isEqualTo("2000");
            assertThat(context.getEnvironment().getProperty("spring.jpa.properties.searchable.auto_fetch_collections")).isEqualTo("false");
        });
    }

    @Test
    void defaultPageSizeMustNotExceedMaximum() {
        runner.withPropertyValues("searchable.default-page-size=21", "searchable.max-page-size=20")
                .run(context -> assertThat(context).hasFailed());
    }
}
