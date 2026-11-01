package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.parser.SearchableParamsParser;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestInvalidKeywordSearchDTO;
import dev.simplecore.searchable.test.dto.TestKeywordSearchDTO;
import dev.simplecore.searchable.test.dto.TestUpdatableSearchDTO;
import dev.simplecore.searchable.test.entity.TestUpdatableEntity;
import dev.simplecore.searchable.test.service.TestUpdatableEntityService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The reserved {@code keyword} parameter matches any keyword field and narrows with the filters. */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class KeywordSearchTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestUpdatableEntityService service;

    @BeforeEach
    void setUp() {
        SearchableFieldUtils.clearCache();
        entityManager.createNativeQuery("DELETE FROM test_updatable").executeUpdate();
        persist("Alpha plan", "ACTIVE", 1);
        persist("Beta plan", "ARCHIVED", 2);
        persist("Gamma", "ALPHA_HOLD", 2);
        persist("100% Delta", "ACTIVE", 3);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void the_keyword_matches_a_record_when_any_keyword_field_contains_it() {
        assertThat(names(Map.of("keyword", "ALPHA"))).containsExactlyInAnyOrder("Gamma");
        assertThat(names(Map.of("keyword", "plan"))).containsExactlyInAnyOrder("Alpha plan", "Beta plan");
        assertThat(names(Map.of("keyword", "ARCHIVED"))).containsExactly("Beta plan");
    }

    @Test
    void the_keyword_is_and_combined_with_the_other_conditions() {
        assertThat(names(Map.of("keyword", "plan", "quantity.equals", "2"))).containsExactly("Beta plan");
        assertThat(names(Map.of("keyword", "Gamma", "quantity.equals", "1"))).isEmpty();
        // A match on a later keyword field is narrowed by the other conditions too.
        assertThat(names(Map.of("keyword", "ARCHIVED", "quantity.equals", "1"))).isEmpty();
        assertThat(names(Map.of("keyword", "ALPHA", "status.equals", "ACTIVE"))).isEmpty();
    }

    @Test
    void wildcards_in_the_keyword_stay_literal() {
        assertThat(names(Map.of("keyword", "%"))).containsExactly("100% Delta");
        assertThat(names(Map.of("keyword", "_"))).containsExactly("Gamma");
        assertThat(names(Map.of("keyword", "' OR '1'='1"))).isEmpty();
    }

    @Test
    void a_blank_keyword_narrows_nothing() {
        assertThat(names(Map.of("keyword", "  "))).hasSize(4);
    }

    @Test
    void a_dto_without_keyword_fields_refuses_the_keyword() {
        SearchableParamsParser<TestUpdatableSearchDTO> parser = new SearchableParamsParser<>(TestUpdatableSearchDTO.class);
        assertThatThrownBy(() -> parser.convert(Map.of("keyword", "x")))
                .isInstanceOf(SearchableParseException.class)
                .hasMessageContaining("TestUpdatableSearchDTO");
    }

    @Test
    void a_keyword_field_that_cannot_be_matched_by_containment_is_refused() {
        SearchableParamsParser<TestInvalidKeywordSearchDTO> parser = new SearchableParamsParser<>(TestInvalidKeywordSearchDTO.class);
        assertThatThrownBy(() -> parser.convert(Map.of("keyword", "x")))
                .isInstanceOf(SearchableParseException.class)
                .hasMessageContaining("status");
    }

    @Test
    void a_key_without_an_operator_is_still_refused() {
        SearchableParamsParser<TestKeywordSearchDTO> parser = new SearchableParamsParser<>(TestKeywordSearchDTO.class);
        assertThatThrownBy(() -> parser.convert(Map.of("search", "x")))
                .isInstanceOf(SearchableParseException.class);
    }

    private void persist(String name, String status, int quantity) {
        TestUpdatableEntity entity = new TestUpdatableEntity();
        entity.setName(name);
        entity.setStatus(status);
        entity.setQuantity(quantity);
        entityManager.persist(entity);
    }

    private List<String> names(Map<String, String> params) {
        SearchCondition<TestKeywordSearchDTO> condition = new SearchableParamsParser<>(TestKeywordSearchDTO.class).convert(params);
        return service.findAllWithSearch(condition).getContent().stream().map(TestUpdatableEntity::getName).toList();
    }
}
