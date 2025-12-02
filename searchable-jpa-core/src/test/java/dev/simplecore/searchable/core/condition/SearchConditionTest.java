package dev.simplecore.searchable.core.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Test class for SearchCondition serialization and deserialization.
 * Verifies the proper handling of conditions, groups, and various data types.
 */
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchConditionTest {

    @Test
    @DisplayName("Test serialization/deserialization of simple conditions")
    void singleConditionSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .greaterThan("age", 20)
                        .and(a -> a
                                .equals("name", "test")
                                .orGreaterThan("age", 10))
                )
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);

        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);  // Three nodes: id condition, age condition, and one group

        // Validate id condition
        assertThat(deserialized.getNodes().get(0))
                .isInstanceOf(SearchCondition.Condition.class);
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age condition
        assertThat(deserialized.getNodes().get(1))
                .isInstanceOf(SearchCondition.Condition.class)
                .extracting(node -> String.valueOf(((SearchCondition.Condition)node).getValue()))
                .isEqualTo("20");
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(ageCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);

        // Validate AND group
        assertThat(deserialized.getNodes().get(2))
                .isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(2);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(2);

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age OR condition
        SearchCondition.Condition ageOrCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
        assertThat(ageOrCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageOrCondition.getValue())).isEqualTo("10");
        assertThat(ageOrCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(ageOrCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Test serialization/deserialization of AND conditions")
    void andConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> a.equals("name", "test"))
                .and(a -> a.greaterThan("age", 20))
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);
        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);

        // Validate id condition
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getOperator()).isNull();  // First condition has null operator

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getOperator()).isEqualTo(LogicalOperator.AND);

        // Validate age condition
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(2);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageCondition.getValue())).isEqualTo("20");
        assertThat(ageCondition.getOperator()).isEqualTo(LogicalOperator.AND);
    }

    @Test
    @DisplayName("Test serialization/deserialization of OR conditions")
    void orConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("status", "ACTIVE"))
                .or(o -> o.equals("status", "PENDING"))
                .or(o -> o.equals("status", "PROCESSING"))
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);
        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);

        // Validate first status condition
        SearchCondition.Condition firstCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(firstCondition.getField()).isEqualTo("status");
        assertThat(firstCondition.getValue()).isEqualTo("ACTIVE");
        assertThat(firstCondition.getOperator()).isNull();  // First condition has null operator

        // Validate second status condition
        SearchCondition.Condition secondCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(secondCondition.getField()).isEqualTo("status");
        assertThat(secondCondition.getValue()).isEqualTo("PENDING");
        assertThat(secondCondition.getOperator()).isEqualTo(LogicalOperator.OR);

        // Validate third status condition
        SearchCondition.Condition thirdCondition = (SearchCondition.Condition) deserialized.getNodes().get(2);
        assertThat(thirdCondition.getField()).isEqualTo("status");
        assertThat(thirdCondition.getValue()).isEqualTo("PROCESSING");
        assertThat(thirdCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("Test serialization/deserialization of nested conditions")
    void nestedConditionsSerializationTest() throws JsonProcessingException {
        // given
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .greaterThan("age", 11)
                        .and(a -> a
                                .equals("name", "test")
                                .orGreaterThan("age", 12))
                )
                .page(0)
                .size(10)
                .build();

        // when
        String json = condition.toJson();
        System.out.println(json);

        SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

        // then
        assertThat(json).doesNotContain("conditions\":[{\"conditions\":[{\"conditions\":");  // Should not have deeply nested conditions
        assertThat(deserialized.getNodes()).hasSize(3);  // Three nodes: id condition, age condition, and one group

        // Validate id condition
        SearchCondition.Condition idCondition = (SearchCondition.Condition) deserialized.getNodes().get(0);
        assertThat(idCondition.getField()).isEqualTo("id");
        assertThat(String.valueOf(idCondition.getValue())).isEqualTo("1");
        assertThat(idCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age condition
        SearchCondition.Condition ageCondition = (SearchCondition.Condition) deserialized.getNodes().get(1);
        assertThat(ageCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageCondition.getValue())).isEqualTo("11");
        assertThat(ageCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);

        // Validate AND group
        assertThat(deserialized.getNodes().get(2))
                .isInstanceOf(SearchCondition.Group.class);
        SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(2);
        assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
        assertThat(andGroup.getNodes()).hasSize(2);

        // Validate name condition
        SearchCondition.Condition nameCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
        assertThat(nameCondition.getField()).isEqualTo("name");
        assertThat(nameCondition.getValue()).isEqualTo("test");
        assertThat(nameCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);

        // Validate age OR condition
        SearchCondition.Condition ageOrCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
        assertThat(ageOrCondition.getField()).isEqualTo("age");
        assertThat(String.valueOf(ageOrCondition.getValue())).isEqualTo("12");
        assertThat(ageOrCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
        assertThat(ageOrCondition.getOperator()).isEqualTo(LogicalOperator.OR);
    }

    // @Test
    // @DisplayName("Test serialization/deserialization with different value types")
    // void differentTypesSerializationTest() throws JsonProcessingException {
    //     // given
    //     SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
    //         .where(w -> w
    //             .equals("id", 1L)
    //             .equals("name", "test")
    //             .equals("age", 20)
    //             .equals("active", true)
    //             .equals("status", "ACTIVE")
    //             .equals("createdAt", LocalDateTime.now())
    //             .equals("score", 4.5))
    //         .page(0)
    //         .size(10)
    //         .build();

    //     // when
    //     String json = condition.toJson();
    //     SearchCondition<TestDTO> deserialized = condition.fromJson(json, TestDTO.class);

    //     // then
    //     assertThat(json).doesNotContain("conditions\":[{\"conditions\":");  // Should not have nested conditions
    //     assertThat(deserialized.getNodes()).hasSize(7);
    //     assertThat(deserialized.getNodes().stream()
    //             .allMatch(node -> node instanceof SearchCondition.Condition))
    //         .isTrue();
    // }

    // @Test
    // @DisplayName("Test serialization/deserialization with @JsonIgnore fields")
    // void jsonIgnoreFieldsTest() throws JsonProcessingException {
    //     // given
    //     SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
    //         .where(w -> w.equals("id", 1))
    //         .page(0)
    //         .size(10)
    //         .build();

    //     // when
    //     String json = condition.toJson();
    //     SearchCondition<TestDTO> deserialized = condition.fromJson(json, TestDTO.class);

    //     // then
    //     assertThat(json).doesNotContain("dtoClass");  // @JsonIgnore field should not be serialized
    //     assertThat(json).doesNotContain("entityField");  // @JsonIgnore field should not be serialized
    //     assertThat(deserialized.getNodes()).hasSize(1);
    //     assertThat(deserialized.getNodes().get(0))
    //         .isInstanceOf(SearchCondition.Condition.class);
    // }

    @Nested
    @DisplayName("SearchConditionBuilder.from() Tests")
    class FromMethodTests {

        @Test
        @DisplayName("from() creates builder with existing conditions")
        void fromExistingConditionTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .page(0)
                    .size(10)
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .and(a -> a.equals("name", "test"))
                    .build();

            // then
            assertThat(extended.getNodes()).hasSize(2);
            assertThat(extended.getPage()).isEqualTo(0);
            assertThat(extended.getSize()).isEqualTo(10);

            SearchCondition.Condition idCondition = (SearchCondition.Condition) extended.getNodes().get(0);
            assertThat(idCondition.getField()).isEqualTo("id");

            SearchCondition.Condition nameCondition = (SearchCondition.Condition) extended.getNodes().get(1);
            assertThat(nameCondition.getField()).isEqualTo("name");
            assertThat(nameCondition.getOperator()).isEqualTo(LogicalOperator.AND);
        }

        @Test
        @DisplayName("from() preserves original SearchCondition immutability")
        void fromPreservesImmutabilityTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .page(0)
                    .size(10)
                    .build();
            int originalNodeCount = original.getNodes().size();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .and(a -> a.equals("name", "test"))
                    .and(a -> a.greaterThan("age", 20))
                    .build();

            // then - original unchanged
            assertThat(original.getNodes()).hasSize(originalNodeCount);
            assertThat(original.getNodes()).hasSize(1);

            // then - extended has additional conditions
            assertThat(extended.getNodes()).hasSize(3);
        }

        @Test
        @DisplayName("from() preserves sort criteria")
        void fromPreservesSortCriteriaTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .sort(s -> s.asc("name").desc("age"))
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .and(a -> a.greaterThan("age", 20))
                    .build();

            // then
            assertThat(extended.getSort()).isNotNull();
            assertThat(extended.getSort().getOrders()).hasSize(2);
            assertThat(extended.getSort().getOrders().get(0).getField()).isEqualTo("name");
            assertThat(extended.getSort().getOrders().get(0).getDirection()).isEqualTo(SearchCondition.Direction.ASC);
            assertThat(extended.getSort().getOrders().get(1).getField()).isEqualTo("age");
            assertThat(extended.getSort().getOrders().get(1).getDirection()).isEqualTo(SearchCondition.Direction.DESC);
        }

        @Test
        @DisplayName("from() allows overriding sort criteria")
        void fromAllowsOverridingSortCriteriaTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .sort(s -> s.asc("name"))
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .sort(s -> s.desc("age"))
                    .build();

            // then - original sort unchanged
            assertThat(original.getSort().getOrders()).hasSize(1);
            assertThat(original.getSort().getOrders().get(0).getField()).isEqualTo("name");

            // then - new sort applied
            assertThat(extended.getSort().getOrders()).hasSize(1);
            assertThat(extended.getSort().getOrders().get(0).getField()).isEqualTo("age");
            assertThat(extended.getSort().getOrders().get(0).getDirection()).isEqualTo(SearchCondition.Direction.DESC);
        }

        @Test
        @DisplayName("from() allows overriding pagination")
        void fromAllowsOverridingPaginationTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .page(0)
                    .size(10)
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .page(5)
                    .size(20)
                    .build();

            // then - original pagination unchanged
            assertThat(original.getPage()).isEqualTo(0);
            assertThat(original.getSize()).isEqualTo(10);

            // then - new pagination applied
            assertThat(extended.getPage()).isEqualTo(5);
            assertThat(extended.getSize()).isEqualTo(20);
        }

        @Test
        @DisplayName("from() handles nested groups correctly")
        void fromWithNestedGroupsTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w
                            .equals("id", 1L)
                            .and(a -> a
                                    .equals("name", "test")
                                    .orGreaterThan("age", 10)))
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .or(o -> o.equals("status", "ACTIVE"))
                    .build();

            // then
            assertThat(extended.getNodes()).hasSize(3);  // id, AND group, OR condition

            // Validate original structure preserved
            assertThat(extended.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            assertThat(extended.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

            // Validate new OR condition added
            SearchCondition.Condition statusCondition = (SearchCondition.Condition) extended.getNodes().get(2);
            assertThat(statusCondition.getField()).isEqualTo("status");
            assertThat(statusCondition.getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("from() with null existing condition throws exception")
        void fromNullConditionThrowsExceptionTest() {
            assertThatThrownBy(() -> SearchConditionBuilder.from(null, TestDTO.class))
                    .isInstanceOf(SearchableValidationException.class);
        }

        @Test
        @DisplayName("from() with null dtoClass throws exception")
        void fromNullDtoClassThrowsExceptionTest() {
            // given
            SearchCondition<TestDTO> existing = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .build();

            // when/then
            assertThatThrownBy(() -> SearchConditionBuilder.from(existing, null))
                    .isInstanceOf(SearchableValidationException.class);
        }

        @Test
        @DisplayName("from() validates new conditions against DTO class")
        void fromValidatesNewConditionsTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .build();

            // when/then - adding invalid field should throw
            assertThatThrownBy(() ->
                    SearchConditionBuilder.from(original, TestDTO.class)
                            .and(a -> a.equals("invalidField", "value"))
                            .build())
                    .isInstanceOf(SearchableValidationException.class);
        }

        @Test
        @DisplayName("from() with empty original condition works correctly")
        void fromEmptyConditionTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .build();

            // then
            assertThat(extended.getNodes()).hasSize(1);
            SearchCondition.Condition idCondition = (SearchCondition.Condition) extended.getNodes().get(0);
            assertThat(idCondition.getField()).isEqualTo("id");
        }
    }

    /**
     * Test DTO class with various field types for testing search conditions
     */
    @Data
    public static class TestDTO {
        @SearchableField(operators = {SearchOperator.EQUALS})
        private Long id;

        @SearchableField(operators = {SearchOperator.EQUALS, SearchOperator.CONTAINS}, sortable = true)
        private String name;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.LESS_THAN,
                SearchOperator.BETWEEN
        }, sortable = true)
        private Integer age;

        @SearchableField(operators = {SearchOperator.EQUALS})
        private Boolean active;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.IN,
                SearchOperator.NOT_IN
        })
        private String status;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.LESS_THAN,
                SearchOperator.BETWEEN
        })
        private LocalDateTime createdAt;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.LESS_THAN,
                SearchOperator.BETWEEN
        })
        private Double score;
    }
} 