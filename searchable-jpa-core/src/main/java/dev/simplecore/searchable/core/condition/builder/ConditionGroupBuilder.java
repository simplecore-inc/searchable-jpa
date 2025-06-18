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
 * <p>The builder maintains two lists:
 * <ul>
 *   <li>conditions: for individual search conditions</li>
 *   <li>groups: for nested groups of conditions</li>
 * </ul>
 */
@Getter
public class ConditionGroupBuilder implements ChainedCondition {
    /**
     * List of individual search conditions in this group.
     */
    private final List<SearchCondition.Node> conditions = new ArrayList<>();

    /**
     * List of nested condition groups.
     */
    private final List<SearchCondition.Node> groups = new ArrayList<>();

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
     * Adds a new condition to the conditions list with a single value.
     *
     * @param logicalOperator the logical operator (AND/OR) for this condition
     * @param field           the field name to search on
     * @param searchOperator  the search operator to apply
     * @param value           the value to search for
     */
    private void addCondition(LogicalOperator logicalOperator, String field,
                              SearchOperator searchOperator, Object value) {
        // Set operator to null if this is the first condition with AND operator
        if (conditions.isEmpty() && logicalOperator == LogicalOperator.AND) {
            logicalOperator = null;
        }
        conditions.add(createCondition(logicalOperator, field, searchOperator, value, null));
    }

    /**
     * Adds a new condition to the conditions list with two values.
     *
     * @param logicalOperator the logical operator (AND/OR) for this condition
     * @param field           the field name to search on
     * @param searchOperator  the search operator to apply
     * @param value           the first value to search for
     * @param value2          the second value to search for
     */
    private void addCondition(LogicalOperator logicalOperator, String field,
                              SearchOperator searchOperator, Object value, Object value2) {
        // Set operator to null if this is the first condition with AND operator
        if (conditions.isEmpty() && logicalOperator == LogicalOperator.AND) {
            logicalOperator = null;
        }
        conditions.add(createCondition(logicalOperator, field, searchOperator, value, value2));
    }

    /**
     * Process a list of conditions by setting the operator of the first condition to null
     * and returning all conditions together.
     * This is used to ensure proper operator precedence in nested conditions.
     *
     * @param conditions the list of conditions to process
     * @return processed list of conditions with the first condition's operator set to null
     */
    private List<SearchCondition.Node> processConditionsWithNullFirstOperator(List<SearchCondition.Node> conditions) {
        if (conditions.isEmpty()) {
            return conditions;
        }

        List<SearchCondition.Node> result = new ArrayList<>();
        SearchCondition.Node firstNode = conditions.get(0);
        if (firstNode instanceof SearchCondition.Condition) {
            SearchCondition.Condition firstCondition = (SearchCondition.Condition) firstNode;
            result.add(new SearchCondition.Condition(
                    null,
                    firstCondition.getField(),
                    firstCondition.getSearchOperator(),
                    firstCondition.getValue(),
                    firstCondition.getValue2(),
                    firstCondition.getEntityField()
            ));
            result.addAll(conditions.subList(1, conditions.size()));
            return result;
        }
        return conditions;
    }

    /**
     * Adds a new group of conditions with the specified logical operator.
     * The first condition in the group will have its operator set to null,
     * while subsequent conditions maintain their operators.
     *
     * @param operator the logical operator (AND/OR) for the group
     * @param consumer the consumer function that builds the group's conditions
     */
    private void addGroup(LogicalOperator operator, Consumer<FirstCondition> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        List<SearchCondition.Node> nodes = new ArrayList<>();
        nodes.addAll(processConditionsWithNullFirstOperator(builder.getConditions()));
        nodes.addAll(builder.getGroups());

        groups.add(new SearchCondition.Group(operator, nodes));
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

        List<SearchCondition.Node> nodes = new ArrayList<>();
        nodes.addAll(processConditionsWithNullFirstOperator(builder.getConditions()));
        nodes.addAll(builder.getGroups());

        if (builder.getGroups().isEmpty()) {
            conditions.addAll(nodes);
        } else {
            groups.add(new SearchCondition.Group(null, nodes));
        }
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