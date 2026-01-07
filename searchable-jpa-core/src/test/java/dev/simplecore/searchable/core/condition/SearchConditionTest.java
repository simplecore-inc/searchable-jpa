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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

        @Test
        @DisplayName("from() preserves fetchFields from original condition")
        void fromPreservesFetchFieldsTest() {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields("author", "category")
                    .build();

            // when
            SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                    .and(a -> a.equals("name", "test"))
                    .build();

            // then
            assertThat(extended.getFetchFields()).containsExactlyInAnyOrder("author", "category");
        }
    }

    @Nested
    @DisplayName("Nested AND/OR Group Tests")
    class NestedAndOrGroupTests {

        @Test
        @DisplayName("and() with condition and nested or() preserves both conditions")
        void andWithConditionAndNestedOrTest() {
            // given - pattern: builder.and(a -> a.lessThanOrEqualTo(...).or(b -> b.isNull(...)))
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .or(b -> b.greaterThan("age", 50)))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - should have 2 nodes: id condition and AND group
            assertThat(nodes).hasSize(2);

            // Validate id condition
            assertThat(nodes.get(0)).isInstanceOf(SearchCondition.Condition.class);
            SearchCondition.Condition idCondition = (SearchCondition.Condition) nodes.get(0);
            assertThat(idCondition.getField()).isEqualTo("id");

            // Validate AND group contains both lessThan condition AND OR group
            assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
            SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
            assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
            assertThat(andGroup.getNodes()).hasSize(2);  // lessThan + OR group

            // First node in group should be the lessThan condition
            assertThat(andGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
            assertThat(lessThanCondition.getField()).isEqualTo("age");
            assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);

            // Second node in group should be the OR group
            assertThat(andGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);
            SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(1);
            assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("or() with condition and nested and() preserves both conditions")
        void orWithConditionAndNestedAndTest() {
            // given - pattern: builder.or(a -> a.equals(...).and(b -> b.greaterThan(...)))
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .or(a -> a
                            .equals("status", "ACTIVE")
                            .and(b -> b.greaterThan("age", 20)))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - should have 2 nodes: id condition and OR group
            assertThat(nodes).hasSize(2);

            // Validate OR group contains both equals condition AND AND group
            assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
            SearchCondition.Group orGroup = (SearchCondition.Group) nodes.get(1);
            assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(orGroup.getNodes()).hasSize(2);  // equals + AND group

            // First node in group should be the equals condition
            assertThat(orGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            SearchCondition.Condition equalsCondition = (SearchCondition.Condition) orGroup.getNodes().get(0);
            assertThat(equalsCondition.getField()).isEqualTo("status");
            assertThat(equalsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
        }

        @Test
        @DisplayName("Multiple conditions with nested or() in and() preserves all conditions")
        void multipleConditionsWithNestedOrTest() {
            // given - pattern mimicking the user's publish/expire scenario
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .or(b -> b.greaterThan("age", 50)))
                    .and(a -> a
                            .greaterThan("score", 3.0)
                            .or(b -> b.equals("score", 0.0)))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - should have 3 nodes: id condition, first AND group, second AND group
            assertThat(nodes).hasSize(3);

            // Both AND groups should contain their respective conditions and OR groups
            SearchCondition.Group firstAndGroup = (SearchCondition.Group) nodes.get(1);
            assertThat(firstAndGroup.getNodes()).hasSize(2);
            assertThat(firstAndGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            assertThat(firstAndGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

            SearchCondition.Group secondAndGroup = (SearchCondition.Group) nodes.get(2);
            assertThat(secondAndGroup.getNodes()).hasSize(2);
            assertThat(secondAndGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            assertThat(secondAndGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);
        }

        @Test
        @DisplayName("JSON serialization preserves nested and/or structure")
        void jsonSerializationWithNestedGroupsTest() throws JsonProcessingException {
            // given
            SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .or(b -> b.greaterThan("age", 50)))
                    .build();

            // when
            String json = original.toJson();
            SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(json, TestDTO.class);

            // then
            assertThat(deserialized.getNodes()).hasSize(2);
            assertThat(deserialized.getNodes().get(1)).isInstanceOf(SearchCondition.Group.class);

            SearchCondition.Group andGroup = (SearchCondition.Group) deserialized.getNodes().get(1);
            assertThat(andGroup.getNodes()).hasSize(2);
        }

        @Test
        @DisplayName("and() with orEquals() preserves both conditions")
        void andWithOrEqualsTest() {
            // given - pattern: builder.and(a -> a.lessThan(...).orEquals(...))
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .orEquals("age", 100))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - orEquals does not create a nested group, so conditions are flattened
            assertThat(nodes).hasSize(3);

            // First: id condition
            SearchCondition.Condition idCondition = (SearchCondition.Condition) nodes.get(0);
            assertThat(idCondition.getField()).isEqualTo("id");

            // Second: lessThan condition with AND operator
            SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) nodes.get(1);
            assertThat(lessThanCondition.getField()).isEqualTo("age");
            assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
            assertThat(lessThanCondition.getOperator()).isEqualTo(LogicalOperator.AND);

            // Third: orEquals condition with OR operator
            SearchCondition.Condition orEqualsCondition = (SearchCondition.Condition) nodes.get(2);
            assertThat(orEqualsCondition.getField()).isEqualTo("age");
            assertThat(orEqualsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
            assertThat(orEqualsCondition.getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("and() with orGreaterThan() preserves both conditions")
        void andWithOrGreaterThanTest() {
            // given
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .orGreaterThan("age", 50))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then
            assertThat(nodes).hasSize(3);

            SearchCondition.Condition orGreaterThanCondition = (SearchCondition.Condition) nodes.get(2);
            assertThat(orGreaterThanCondition.getField()).isEqualTo("age");
            assertThat(orGreaterThanCondition.getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
            assertThat(orGreaterThanCondition.getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("and() with multiple orXXX() methods preserves all conditions")
        void andWithMultipleOrMethodsTest() {
            // given - pattern: builder.and(a -> a.lessThan(...).orEquals(...).orGreaterThan(...))
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .orEquals("age", 50)
                            .orGreaterThan("age", 100))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - all conditions should be present
            assertThat(nodes).hasSize(4);

            // Validate all conditions
            assertThat(((SearchCondition.Condition) nodes.get(0)).getField()).isEqualTo("id");
            assertThat(((SearchCondition.Condition) nodes.get(1)).getField()).isEqualTo("age");
            assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(((SearchCondition.Condition) nodes.get(3)).getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
            assertThat(((SearchCondition.Condition) nodes.get(3)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("and() with orXXX() combined with nested or() preserves all conditions")
        void andWithOrMethodsAndNestedOrTest() {
            // given - mixed pattern: orEquals() + nested or()
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 30)
                            .orEquals("age", 50)
                            .or(b -> b.greaterThan("age", 100)))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - should have id condition + AND group (which contains conditions and nested OR group)
            assertThat(nodes).hasSize(2);

            // Validate AND group contains all conditions including orEquals and nested or()
            assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);
            SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
            assertThat(andGroup.getOperator()).isEqualTo(LogicalOperator.AND);
            assertThat(andGroup.getNodes()).hasSize(3);  // lessThan + orEquals + OR group

            // First: lessThan condition
            assertThat(andGroup.getNodes().get(0)).isInstanceOf(SearchCondition.Condition.class);
            SearchCondition.Condition lessThanCondition = (SearchCondition.Condition) andGroup.getNodes().get(0);
            assertThat(lessThanCondition.getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);

            // Second: orEquals condition
            assertThat(andGroup.getNodes().get(1)).isInstanceOf(SearchCondition.Condition.class);
            SearchCondition.Condition orEqualsCondition = (SearchCondition.Condition) andGroup.getNodes().get(1);
            assertThat(orEqualsCondition.getSearchOperator()).isEqualTo(SearchOperator.EQUALS);
            assertThat(orEqualsCondition.getOperator()).isEqualTo(LogicalOperator.OR);

            // Third: nested OR group
            assertThat(andGroup.getNodes().get(2)).isInstanceOf(SearchCondition.Group.class);
            SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(2);
            assertThat(orGroup.getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("Simulating publish/expire scenario with orIsNull()")
        void publishExpireScenarioWithOrIsNullTest() {
            // given - simulating: (publishAt <= now OR publishAt IS NULL) AND (expireAt > now OR expireAt IS NULL)
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThanOrEqualTo("age", 30)
                            .orIsNull("age"))
                    .and(a -> a
                            .greaterThan("score", 3.0)
                            .orIsNull("score"))
                    .build();

            // when
            List<SearchCondition.Node> nodes = condition.getNodes();

            // then - should have 5 nodes: id, lessThanOrEqualTo, orIsNull, greaterThan, orIsNull
            assertThat(nodes).hasSize(5);

            assertThat(((SearchCondition.Condition) nodes.get(1)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN_OR_EQUAL_TO);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.IS_NULL);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(((SearchCondition.Condition) nodes.get(3)).getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN);
            assertThat(((SearchCondition.Condition) nodes.get(4)).getSearchOperator()).isEqualTo(SearchOperator.IS_NULL);
            assertThat(((SearchCondition.Condition) nodes.get(4)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        // ========== Additional orXXX() Method Tests ==========

        @Test
        @DisplayName("orNotEquals() preserves condition with OR operator")
        void orNotEqualsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.equals("status", "ACTIVE").orNotEquals("status", "DELETED"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_EQUALS);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orGreaterThanOrEqualTo() preserves condition with OR operator")
        void orGreaterThanOrEqualToTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.lessThan("age", 20).orGreaterThanOrEqualTo("age", 50))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.GREATER_THAN_OR_EQUAL_TO);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orLessThan() preserves condition with OR operator")
        void orLessThanTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.greaterThan("age", 50).orLessThan("age", 20))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orLessThanOrEqualTo() preserves condition with OR operator")
        void orLessThanOrEqualToTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.greaterThan("age", 50).orLessThanOrEqualTo("age", 20))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.LESS_THAN_OR_EQUAL_TO);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orContains() preserves condition with OR operator")
        void orContainsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.startsWith("name", "John").orContains("name", "Smith"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.CONTAINS);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orNotContains() preserves condition with OR operator")
        void orNotContainsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.contains("name", "John").orNotContains("name", "Doe"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_CONTAINS);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orStartsWith() preserves condition with OR operator")
        void orStartsWithTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.endsWith("name", "son").orStartsWith("name", "John"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.STARTS_WITH);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orNotStartsWith() preserves condition with OR operator")
        void orNotStartsWithTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.startsWith("name", "John").orNotStartsWith("name", "Jane"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_STARTS_WITH);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orEndsWith() preserves condition with OR operator")
        void orEndsWithTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.startsWith("name", "John").orEndsWith("name", "son"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.ENDS_WITH);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orNotEndsWith() preserves condition with OR operator")
        void orNotEndsWithTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.endsWith("name", "son").orNotEndsWith("name", "ley"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_ENDS_WITH);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orIsNull() preserves condition with OR operator")
        void orIsNullTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.greaterThan("age", 20).orIsNull("age"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.IS_NULL);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orIsNotNull() preserves condition with OR operator")
        void orIsNotNullTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.isNull("age").orIsNotNull("score"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.IS_NOT_NULL);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orIn() preserves condition with OR operator")
        void orInTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.equals("status", "ACTIVE").orIn("status", Arrays.asList("PENDING", "PROCESSING")))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.IN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orNotIn() preserves condition with OR operator")
        void orNotInTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.equals("status", "ACTIVE").orNotIn("status", Arrays.asList("DELETED", "ARCHIVED")))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_IN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orBetween() preserves condition with OR operator")
        void orBetweenTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.lessThan("age", 20).orBetween("age", 50, 60))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.BETWEEN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("orNotBetween() preserves condition with OR operator")
        void orNotBetweenTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.between("age", 30, 40).orNotBetween("age", 50, 60))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getSearchOperator()).isEqualTo(SearchOperator.NOT_BETWEEN);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        // ========== Edge Case Tests ==========

        @Test
        @DisplayName("Complex: Multiple orXXX() methods chained together")
        void multipleOrMethodsChainedTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .equals("age", 25)
                            .orLessThan("age", 18)
                            .orGreaterThan("age", 65)
                            .orIsNull("age"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(5);

            // All orXXX() conditions should have OR operator
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(((SearchCondition.Condition) nodes.get(3)).getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(((SearchCondition.Condition) nodes.get(4)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("Complex: orXXX() with nested or() in same group")
        void orMethodWithNestedOrInSameGroupTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 20)
                            .orEquals("age", 25)
                            .orGreaterThan("age", 60)
                            .or(b -> b.isNull("age")))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            // Should have id condition and AND group
            assertThat(nodes).hasSize(2);
            assertThat(nodes.get(1)).isInstanceOf(SearchCondition.Group.class);

            SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
            // Should have: lessThan, orEquals, orGreaterThan, OR group
            assertThat(andGroup.getNodes()).hasSize(4);
        }

        @Test
        @DisplayName("Complex: Multiple nested groups with orXXX()")
        void multipleNestedGroupsWithOrMethodsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a
                            .lessThan("age", 20)
                            .orIsNull("age")
                            .or(b -> b.greaterThan("age", 60)))
                    .and(a -> a
                            .equals("status", "ACTIVE")
                            .orIn("status", Arrays.asList("PENDING", "PROCESSING"))
                            .or(b -> b.isNull("status")))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            // id condition + 2 AND groups
            assertThat(nodes).hasSize(3);

            SearchCondition.Group firstGroup = (SearchCondition.Group) nodes.get(1);
            SearchCondition.Group secondGroup = (SearchCondition.Group) nodes.get(2);

            // Each group has: condition + orXXX + nested OR group
            assertThat(firstGroup.getNodes()).hasSize(3);
            assertThat(secondGroup.getNodes()).hasSize(3);
        }

        @Test
        @DisplayName("Edge: Single condition with orXXX() only")
        void singleConditionWithOrOnlyTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L).orEquals("id", 2L))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(2);
            assertThat(((SearchCondition.Condition) nodes.get(0)).getOperator()).isNull();
            assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
        }

        @Test
        @DisplayName("Edge: Empty and() with only orXXX()")
        void emptyAndWithOrOnlyTest() {
            // This tests when first condition is followed only by orXXX
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.equals("age", 25).orEquals("age", 30).orEquals("age", 35))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(4); // id, age=25, age=30, age=35
        }

        @Test
        @DisplayName("Edge: Deeply nested and/or groups")
        void deeplyNestedGroupsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w
                            .equals("id", 1L)
                            .and(a -> a
                                    .lessThan("age", 30)
                                    .or(b -> b
                                            .greaterThan("age", 50)
                                            .and(c -> c.equals("status", "VIP")))))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(2); // id condition + AND group

            SearchCondition.Group andGroup = (SearchCondition.Group) nodes.get(1);
            assertThat(andGroup.getNodes()).hasSize(2); // lessThan + OR group

            SearchCondition.Group orGroup = (SearchCondition.Group) andGroup.getNodes().get(1);
            assertThat(orGroup.getNodes()).hasSize(2); // greaterThan + AND group
        }

        @Test
        @DisplayName("Edge: or() followed by and() in same builder")
        void orFollowedByAndTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .or(o -> o.equals("id", 2L))
                    .and(a -> a.greaterThan("age", 20))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(3);
            assertThat(((SearchCondition.Condition) nodes.get(0)).getOperator()).isNull();
            assertThat(((SearchCondition.Condition) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);
            assertThat(((SearchCondition.Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.AND);
        }

        @Test
        @DisplayName("Edge: Alternating and/or with orXXX")
        void alternatingAndOrWithOrMethodsTest() {
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .and(a -> a.greaterThan("age", 20).orIsNull("age"))
                    .or(o -> o.equals("status", "SPECIAL"))
                    .and(a -> a.lessThan("score", 100.0).orIsNull("score"))
                    .build();

            List<SearchCondition.Node> nodes = condition.getNodes();
            assertThat(nodes).hasSize(6);
        }
    }

    @Nested
    @DisplayName("FetchFields Tests")
    class FetchFieldsTests {

        @Test
        @DisplayName("fetchFields with varargs sets fields correctly")
        void fetchFieldsVarargsTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields("author", "author.profile", "category")
                    .build();

            // then
            assertThat(condition.getFetchFields())
                    .containsExactlyInAnyOrder("author", "author.profile", "category");
        }

        @Test
        @DisplayName("fetchFields with Set sets fields correctly")
        void fetchFieldsSetTest() {
            // given
            Set<String> fields = new HashSet<>(Arrays.asList("author", "category"));

            // when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields(fields)
                    .build();

            // then
            assertThat(condition.getFetchFields())
                    .containsExactlyInAnyOrder("author", "category");
        }

        @Test
        @DisplayName("fetchFields is ignored during JSON deserialization")
        void fetchFieldsJsonIgnoreTest() throws JsonProcessingException {
            // given - JSON with fetchFields (simulating malicious client input)
            String jsonWithFetchFields = """
                    {
                      "conditions": [
                        {
                          "field": "id",
                          "searchOperator": "EQUALS",
                          "value": 1
                        }
                      ],
                      "fetchFields": ["author", "category"],
                      "page": 0,
                      "size": 10
                    }
                    """;

            // when
            SearchCondition<TestDTO> deserialized = SearchCondition.fromJson(jsonWithFetchFields, TestDTO.class);

            // then - fetchFields should be empty (ignored during deserialization)
            assertThat(deserialized.getFetchFields()).isEmpty();
        }

        @Test
        @DisplayName("fetchFields is not included in JSON serialization")
        void fetchFieldsNotSerializedTest() throws JsonProcessingException {
            // given
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .fetchFields("author", "category")
                    .build();

            // when
            String json = condition.toJson();

            // then - fetchFields should not appear in JSON
            assertThat(json).doesNotContain("fetchFields");
            assertThat(json).doesNotContain("author");
            assertThat(json).doesNotContain("category");
        }

        @Test
        @DisplayName("fetchFields can be set after other builder methods")
        void fetchFieldsMethodChainingTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .sort(s -> s.asc("name"))
                    .page(0)
                    .size(10)
                    .fetchFields("author")
                    .build();

            // then
            assertThat(condition.getFetchFields()).containsExactly("author");
            assertThat(condition.getSort()).isNotNull();
            assertThat(condition.getPage()).isEqualTo(0);
            assertThat(condition.getSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("empty fetchFields returns empty set")
        void emptyFetchFieldsTest() {
            // given & when
            SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                    .where(w -> w.equals("id", 1L))
                    .build();

            // then
            assertThat(condition.getFetchFields()).isNotNull();
            assertThat(condition.getFetchFields()).isEmpty();
        }
    }

    /**
     * Test DTO class with various field types for testing search conditions
     */
    @Data
    public static class TestDTO {
        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Long id;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.CONTAINS,
                SearchOperator.NOT_CONTAINS,
                SearchOperator.STARTS_WITH,
                SearchOperator.NOT_STARTS_WITH,
                SearchOperator.ENDS_WITH,
                SearchOperator.NOT_ENDS_WITH,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        }, sortable = true)
        private String name;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        }, sortable = true)
        private Integer age;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Boolean active;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.IN,
                SearchOperator.NOT_IN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private String status;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private LocalDateTime createdAt;

        @SearchableField(operators = {
                SearchOperator.EQUALS,
                SearchOperator.NOT_EQUALS,
                SearchOperator.GREATER_THAN,
                SearchOperator.GREATER_THAN_OR_EQUAL_TO,
                SearchOperator.LESS_THAN,
                SearchOperator.LESS_THAN_OR_EQUAL_TO,
                SearchOperator.BETWEEN,
                SearchOperator.NOT_BETWEEN,
                SearchOperator.IS_NULL,
                SearchOperator.IS_NOT_NULL
        })
        private Double score;
    }
} 