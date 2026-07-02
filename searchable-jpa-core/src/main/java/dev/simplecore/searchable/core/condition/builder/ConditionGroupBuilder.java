package dev.simplecore.searchable.core.condition.builder;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A builder class for constructing groups of search conditions.
 * This class implements the {@link ChainedCondition} interface to provide a fluent API for building
 * complex search conditions with both AND and OR operations.
 *
 * <p>Conditions and nested groups are kept in a single order-preserving list, so the SQL logic
 * reflects the actual call order (e.g. {@code equals(...).or(...).equals(...)} groups the OR where
 * it was written).
 */
@Getter
public class ConditionGroupBuilder implements ChainedCondition {
    /**
     * Ordered list of nodes (individual conditions and nested groups) in the order they were added.
     */
    private final List<SearchCondition.Node> nodes = new ArrayList<>();

    /**
     * The DTO class type used for field name resolution.
     */
    private final Class<?> dtoClass;

    /**
     * Creates a new ConditionGroupBuilder for the specified DTO class.
     *
     * @param dtoClass the DTO class type used for field name resolution
     */
    public ConditionGroupBuilder(Class<?> dtoClass) {
        this.dtoClass = dtoClass;
    }

    /**
     * Creates a new search condition with the specified parameters.
     *
     * @param logicalOperator the logical operator (AND/OR) for this condition
     * @param field           the field name to search on
     * @param searchOperator  the search operator to apply
     * @param value           the value to search for
     * @param value2          the second value for operators that require two values (e.g., BETWEEN)
     * @return a new SearchCondition.Condition instance
     */
    private SearchCondition.Condition createCondition(LogicalOperator logicalOperator, String field,
                                                      SearchOperator searchOperator, Object value, Object value2) {
        String entityField = SearchableFieldUtils.getEntityFieldFromDto(dtoClass, field);
        return new SearchCondition.Condition(logicalOperator, field, searchOperator, value, value2, entityField);
    }

    /**
     * Adds a new condition with a single value.
     *
     * @param logicalOperator the logical operator (AND/OR) for this condition
     * @param field           the field name to search on
     * @param searchOperator  the search operator to apply
     * @param value           the value to search for
     */
    private void addCondition(LogicalOperator logicalOperator, String field,
                              SearchOperator searchOperator, Object value) {
        addCondition(logicalOperator, field, searchOperator, value, null);
    }

    /**
     * Adds a new condition with two values.
     * The first node in a group carries no logical operator (regardless of AND/OR), while
     * subsequent conditions keep the operator they were declared with.
     *
     * @param logicalOperator the logical operator (AND/OR) for this condition
     * @param field           the field name to search on
     * @param searchOperator  the search operator to apply
     * @param value           the first value to search for
     * @param value2          the second value to search for
     */
    private void addCondition(LogicalOperator logicalOperator, String field,
                              SearchOperator searchOperator, Object value, Object value2) {
        LogicalOperator effectiveOperator = nodes.isEmpty() ? null : logicalOperator;
        nodes.add(createCondition(effectiveOperator, field, searchOperator, value, value2));
    }

    /**
     * Adds a new nested group of conditions with the specified logical operator.
     * Empty groups (where the callback added nothing) are skipped so they cannot fail at build time.
     *
     * @param operator the logical operator (AND/OR) for the group
     * @param consumer the consumer function that builds the group's conditions
     */
    private void addGroup(LogicalOperator operator, Consumer<FirstCondition> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        if (builder.getNodes().isEmpty()) {
            return;
        }

        nodes.add(new SearchCondition.Group(operator, builder.getNodes()));
    }

    @Override
    public ChainedCondition equals(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.EQUALS, value);
        return this;
    }

    @Override
    public ChainedCondition notEquals(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_EQUALS, value);
        return this;
    }

    @Override
    public ChainedCondition greaterThan(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.GREATER_THAN, value);
        return this;
    }

    @Override
    public ChainedCondition greaterThanOrEqualTo(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.GREATER_THAN_OR_EQUAL_TO, value);
        return this;
    }

    @Override
    public ChainedCondition lessThan(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.LESS_THAN, value);
        return this;
    }

    @Override
    public ChainedCondition lessThanOrEqualTo(String field, Object value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.LESS_THAN_OR_EQUAL_TO, value);
        return this;
    }

    @Override
    public ChainedCondition contains(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.CONTAINS, value);
        return this;
    }

    @Override
    public ChainedCondition notContains(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_CONTAINS, value);
        return this;
    }

    @Override
    public ChainedCondition startsWith(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.STARTS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition notStartsWith(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_STARTS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition endsWith(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.ENDS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition notEndsWith(String field, String value) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_ENDS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition isNull(String field) {
        addCondition(LogicalOperator.AND, field, SearchOperator.IS_NULL, null);
        return this;
    }

    @Override
    public ChainedCondition isNotNull(String field) {
        addCondition(LogicalOperator.AND, field, SearchOperator.IS_NOT_NULL, null);
        return this;
    }

    @Override
    public ChainedCondition in(String field, List<?> values) {
        addCondition(LogicalOperator.AND, field, SearchOperator.IN, values);
        return this;
    }

    @Override
    public ChainedCondition notIn(String field, List<?> values) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_IN, values);
        return this;
    }

    @Override
    public ChainedCondition between(String field, Object start, Object end) {
        addCondition(LogicalOperator.AND, field, SearchOperator.BETWEEN, start, end);
        return this;
    }

    @Override
    public ChainedCondition notBetween(String field, Object start, Object end) {
        addCondition(LogicalOperator.AND, field, SearchOperator.NOT_BETWEEN, start, end);
        return this;
    }

    @Override
    public ChainedCondition orEquals(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.EQUALS, value);
        return this;
    }

    @Override
    public ChainedCondition orNotEquals(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_EQUALS, value);
        return this;
    }

    @Override
    public ChainedCondition orGreaterThan(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.GREATER_THAN, value);
        return this;
    }

    @Override
    public ChainedCondition orGreaterThanOrEqualTo(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.GREATER_THAN_OR_EQUAL_TO, value);
        return this;
    }

    @Override
    public ChainedCondition orLessThan(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.LESS_THAN, value);
        return this;
    }

    @Override
    public ChainedCondition orLessThanOrEqualTo(String field, Object value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.LESS_THAN_OR_EQUAL_TO, value);
        return this;
    }

    @Override
    public ChainedCondition orContains(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.CONTAINS, value);
        return this;
    }

    @Override
    public ChainedCondition orNotContains(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_CONTAINS, value);
        return this;
    }

    @Override
    public ChainedCondition orStartsWith(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.STARTS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition orNotStartsWith(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_STARTS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition orEndsWith(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.ENDS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition orNotEndsWith(String field, String value) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_ENDS_WITH, value);
        return this;
    }

    @Override
    public ChainedCondition orIsNull(String field) {
        addCondition(LogicalOperator.OR, field, SearchOperator.IS_NULL, null);
        return this;
    }

    @Override
    public ChainedCondition orIsNotNull(String field) {
        addCondition(LogicalOperator.OR, field, SearchOperator.IS_NOT_NULL, null);
        return this;
    }

    @Override
    public ChainedCondition orIn(String field, List<?> values) {
        addCondition(LogicalOperator.OR, field, SearchOperator.IN, values);
        return this;
    }

    @Override
    public ChainedCondition orNotIn(String field, List<?> values) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_IN, values);
        return this;
    }

    @Override
    public ChainedCondition orBetween(String field, Object start, Object end) {
        addCondition(LogicalOperator.OR, field, SearchOperator.BETWEEN, start, end);
        return this;
    }

    @Override
    public ChainedCondition orNotBetween(String field, Object start, Object end) {
        addCondition(LogicalOperator.OR, field, SearchOperator.NOT_BETWEEN, start, end);
        return this;
    }

    @Override
    public ChainedCondition where(Consumer<FirstCondition> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        // Inline the sub-builder's nodes in call order; its first node already carries no operator.
        nodes.addAll(builder.getNodes());
        return this;
    }

    @Override
    public ChainedCondition and(Consumer<FirstCondition> consumer) {
        addGroup(LogicalOperator.AND, consumer);
        return this;
    }

    @Override
    public ChainedCondition or(Consumer<FirstCondition> consumer) {
        addGroup(LogicalOperator.OR, consumer);
        return this;
    }

}