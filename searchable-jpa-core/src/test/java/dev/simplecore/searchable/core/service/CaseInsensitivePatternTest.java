package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.core.utils.SearchablePatternMatchingHolder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestUpdatableSearchDTO;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.service.TestUpdatableEntityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pattern operators match as the database compares strings by default, and ignore case when
 * {@link SearchablePatternMatchingHolder} says so.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class CaseInsensitivePatternTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestUpdatableEntityService service;

    @BeforeEach
    void setUp() {
        SearchableFieldUtils.clearCache();
        entityManager.createNativeQuery("DELETE FROM test_updatable").executeUpdate();
        for (String name : List.of("Pro Monthly", "PRO yearly", "Free")) {
            TestUpdatableEntity entity = new TestUpdatableEntity();
            entity.setName(name);
            entity.setStatus("ACTIVE");
            entity.setCategory("PLAN");
            entityManager.persist(entity);
        }
        entityManager.flush();
        entityManager.clear();
    }

    @AfterEach
    void restoreDefault() {
        SearchablePatternMatchingHolder.setCaseInsensitive(false);
    }

    @Test
    void by_default_a_pattern_matches_case_sensitively() {
        assertThat(namesContaining("pro")).isEmpty();
        assertThat(namesContaining("Pro")).containsExactly("Pro Monthly");
    }

    @Test
    void when_case_is_ignored_a_pattern_matches_every_casing() {
        SearchablePatternMatchingHolder.setCaseInsensitive(true);

        assertThat(namesContaining("pro")).containsExactlyInAnyOrder("Pro Monthly", "PRO yearly");
        assertThat(namesContaining("YEAR")).containsExactly("PRO yearly");
    }

    @Test
    void when_case_is_ignored_wildcards_in_the_value_stay_literal() {
        SearchablePatternMatchingHolder.setCaseInsensitive(true);

        assertThat(namesContaining("%")).isEmpty();
    }

    private List<String> namesContaining(String value) {
        SearchCondition<TestUpdatableSearchDTO> condition = SearchConditionBuilder.create(TestUpdatableSearchDTO.class)
                .where(w -> w.contains("name", value))
                .build();
        return service.findAllWithSearch(condition).getContent().stream().map(TestUpdatableEntity::getName).toList();
    }
}
