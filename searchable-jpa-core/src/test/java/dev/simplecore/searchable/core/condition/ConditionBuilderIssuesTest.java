package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.SearchConditionTest.TestDTO;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Category 3 (condition builder DSL) issues 3-1 through 3-5.
 */
class ConditionBuilderIssuesTest {

    // ---- 3-1: nested groups keep their actual call position ----

    @Test
    @DisplayName("3-1: an or() group written between two conditions stays between them")
    void nestedGroupKeepsCallOrder() {
        // Intent: (status = ACTIVE OR name = admin) AND id = 1
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("status", "ACTIVE")
                        .or(n -> n.equals("name", "admin"))
                        .equals("id", 1L))
                .build();

        List<Node> nodes = condition.getNodes();
        assertThat(nodes).hasSize(3);

        // node 0: status condition
        assertThat(nodes.get(0)).isInstanceOf(Condition.class);
        assertThat(((Condition) nodes.get(0)).getField()).isEqualTo("status");

        // node 1: the OR group appears in the MIDDLE, exactly where it was written
        assertThat(nodes.get(1)).isInstanceOf(Group.class);
        assertThat(((Group) nodes.get(1)).getOperator()).isEqualTo(LogicalOperator.OR);

        // node 2: id condition, combined with AND, appears LAST
        assertThat(nodes.get(2)).isInstanceOf(Condition.class);
        assertThat(((Condition) nodes.get(2)).getField()).isEqualTo("id");
        assertThat(((Condition) nodes.get(2)).getOperator()).isEqualTo(LogicalOperator.AND);
    }

    // ---- 3-2: empty and()/or() groups are skipped ----

    @Test
    @DisplayName("3-2: an empty nested and() group is skipped instead of producing an empty group node")
    void emptyNestedGroupSkipped() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L).and(inner -> { /* nothing added */ }))
                .build();

        List<Node> nodes = condition.getNodes();
        assertThat(nodes).hasSize(1);
        assertThat(nodes).noneMatch(node -> node instanceof Group && node.getNodes().isEmpty());
    }

    @Test
    @DisplayName("3-2: an empty top-level and() group is skipped")
    void emptyTopLevelGroupSkipped() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .and(a -> { /* nothing added */ })
                .build();

        assertThat(condition.getNodes()).hasSize(1);
    }

    // ---- 3-3: where() may be called repeatedly (Javadoc now matches behavior) ----

    @Test
    @DisplayName("3-3: calling where() more than once accumulates conditions without throwing")
    void repeatedWhereAccumulates() {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("id", 1L))
                .where(w -> w.equals("name", "test"))
                .build();

        assertThat(condition.getNodes()).hasSize(2);
    }

    // ---- 3-4: from() deep-copies nodes so the original stays immutable ----

    @Test
    @DisplayName("3-4: mutating a node obtained via from() does not affect the original condition")
    void fromDeepCopiesNodes() {
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.equals("status", "ACTIVE"))
                .build();
        LogicalOperator originalOperator = ((Condition) original.getNodes().get(0)).getOperator();

        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class)
                .and(a -> a.equals("name", "x"))
                .build();

        // Mutate the copied first node.
        ((Condition) extended.getNodes().get(0)).setOperator(LogicalOperator.OR);

        // The original node must be untouched.
        assertThat(((Condition) original.getNodes().get(0)).getOperator()).isEqualTo(originalOperator);
        assertThat(((Condition) original.getNodes().get(0)).getOperator()).isNotEqualTo(LogicalOperator.OR);
    }

    @Test
    @DisplayName("3-4: from() deep-copies nested group children too")
    void fromDeepCopiesNestedGroups() {
        SearchCondition<TestDTO> original = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w
                        .equals("id", 1L)
                        .and(a -> a.equals("name", "n").orEquals("name", "m")))
                .build();
        Group originalGroup = (Group) original.getNodes().get(1);
        LogicalOperator originalChildOperator = ((Condition) originalGroup.getNodes().get(0)).getOperator();

        SearchCondition<TestDTO> extended = SearchConditionBuilder.from(original, TestDTO.class).build();
        Group extendedGroup = (Group) extended.getNodes().get(1);
        ((Condition) extendedGroup.getNodes().get(0)).setOperator(LogicalOperator.OR);

        assertThat(((Condition) originalGroup.getNodes().get(0)).getOperator()).isEqualTo(originalChildOperator);
    }

    // ---- 3-5: the first condition carries no operator, even for orXXX() ----

    @Test
    @DisplayName("3-5: a leading orEquals() produces a null operator on the first condition")
    void firstOrConditionHasNullOperator() throws Exception {
        SearchCondition<TestDTO> condition = SearchConditionBuilder.create(TestDTO.class)
                .where(w -> w.orEquals("id", 1L))
                .build();

        Condition first = (Condition) condition.getNodes().get(0);
        assertThat(first.getOperator()).isNull();

        // And the serialized JSON does not carry a stray "operator":"OR" for the first condition.
        String json = condition.toJson();
        assertThat(json).doesNotContain("\"operator\"");
    }
}
