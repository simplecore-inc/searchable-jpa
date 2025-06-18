package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.builder.*;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.validator.SearchConditionValidator;
import dev.simplecore.searchable.core.condition.validator.SearchableFieldValidator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A fluent builder for constructing {@link SearchCondition} instances with a state-based approach.
 * This builder enforces a specific order of method calls to ensure valid search condition construction:
 *
 * <p>The builder follows these state transitions:
 * <ul>
 *   <li>Initial State: Only {@code where(...)} is allowed</li>
 *   <li>After {@code where(...)}: {@code and(...)}, {@code or(...)}, {@code sort(...)}, {@code page(...)}, {@code size(...)}, and {@code build()} are allowed</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * SearchCondition condition = SearchConditionBuilder.create(UserDTO.class)
 *     .where(w -> w
 *         .equals("name", "John")
 *         .greaterThan("age", 18))
 *     .and(a -> a
 *         .equals("status", "ACTIVE"))
 *     .sort(s -> s
 *         .asc("name")
 *         .desc("age"))
 *     .page(0)
 *     .size(10)
 *     .build();
 * }</pre>
 *
 * <p>When created with a DTO class, the builder performs validation against {@code @SearchableField}
 * annotations and other constraints before building the final {@link SearchCondition}.
 */
public class SearchConditionBuilder<D> {
    /**
     * The search condition being built.
     */
    private final SearchCondition<D> condition;

    /**
     * The DTO class type used for field validation.
     */
    private final Class<D> dtoClass;

    /**
     * Creates a new builder instance with DTO class validation.
     *
     * @param dtoClass the DTO class type to validate against
     * @throws IllegalArgumentException if dtoClass is null
     */
    private SearchConditionBuilder(Class<D> dtoClass) {
        if (dtoClass == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("builder.dto.class.required"));
        }
        this.condition = new SearchCondition<>();
        this.dtoClass = dtoClass;
    }

    /**
     * Creates a new builder instance with DTO class validation.
     *
     * @param <D>      the type of the DTO class
     * @param dtoClass the DTO class type to validate against
     * @return a new SearchConditionBuilder instance
     * @throws IllegalArgumentException if dtoClass is null
     */
    public static <D> SearchConditionBuilder<D> create(Class<D> dtoClass) {
        return new SearchConditionBuilder<>(dtoClass);
    }

    /**
     * Starts building the search condition with an initial group of conditions.
     * This method must be called first before any other builder methods.
     * It handles both direct conditions and nested groups.
     *
     * @param consumer a consumer that configures the initial conditions
     * @return a chained builder for additional configuration
     * @throws IllegalStateException if where() is called after the first group
     */
    public SearchConditionBuilder<D> where(Consumer<ConditionGroupBuilder> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        condition.getNodes().addAll(builder.getConditions());
        condition.getNodes().addAll(builder.getGroups());

        return this;
    }

    /**
     * Adds an AND group of conditions.
     *
     * @param consumer a consumer that configures the AND conditions
     * @return a chained builder for additional configuration
     */
    public SearchConditionBuilder<D> and(Consumer<ConditionGroupBuilder> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        // Do not group single conditions or simple AND conditions
        if (builder.getGroups().isEmpty()) {
            for (SearchCondition.Node node : builder.getConditions()) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                condition.setOperator(LogicalOperator.AND);
                this.condition.getNodes().add(condition);
            }
        } else {
            // Add as a group only if there are groups
            for (SearchCondition.Node node : builder.getGroups()) {
                SearchCondition.Group group = (SearchCondition.Group) node;
                group.setOperator(LogicalOperator.AND);
                this.condition.getNodes().add(group);
            }
        }

        return this;
    }

    /**
     * Adds an OR group of conditions.
     *
     * @param consumer a consumer that configures the OR conditions
     * @return a chained builder for additional configuration
     */
    public SearchConditionBuilder<D> or(Consumer<ConditionGroupBuilder> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        // Do not group single conditions or simple OR conditions
        if (builder.getGroups().isEmpty()) {
            for (SearchCondition.Node node : builder.getConditions()) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                condition.setOperator(LogicalOperator.OR);
                this.condition.getNodes().add(condition);
            }
        } else {
            // Add as a group only if there are groups
            for (SearchCondition.Node node : builder.getGroups()) {
                SearchCondition.Group group = (SearchCondition.Group) node;
                group.setOperator(LogicalOperator.OR);
                this.condition.getNodes().add(group);
            }
        }

        return this;
    }

    /**
     * Adds sort criteria to the search condition.
     *
     * @param consumer a consumer that configures the sort criteria
     * @return a chained builder for additional configuration
     */
    public ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer) {
        SearchCondition.Sort sort = new SearchCondition.Sort();
        consumer.accept(new SortBuilder(sort, dtoClass));
        condition.setSort(sort);
        return new ChainedSearchConditionImpl<>(this);
    }

    /**
     * Sets the page number for pagination (zero-based).
     *
     * @param page the page number
     * @return a chained builder for additional configuration
     */
    public ChainedSearchCondition<D> page(int page) {
        condition.setPage(page);
        return new ChainedSearchConditionImpl<>(this);
    }

    /**
     * Sets the page size for pagination.
     *
     * @param size the number of items per page
     * @return a chained builder for additional configuration
     */
    public ChainedSearchCondition<D> size(int size) {
        condition.setSize(size);
        return new ChainedSearchConditionImpl<>(this);
    }

    /**
     * Builds and validates the final search condition.
     * If a DTO class was provided, validates all fields and constraints.
     *
     * @return the built and validated SearchCondition
     * @throws javax.validation.ValidationException if validation fails
     */
    public SearchCondition<D> build() {
        if (dtoClass != null) {
            // First validate @SearchableField annotations
            new SearchableFieldValidator<>(dtoClass, condition).validate();

            // Then validate constraints
            new SearchConditionValidator<>(dtoClass, condition).validate();
        }

        return condition;
    }

    /**
     * Internal method to add a new group of conditions with a logical operator.
     * This method handles both direct conditions and nested groups.
     *
     * @param operator the logical operator for the group
     * @param consumer the consumer function that builds the group's conditions
     */
    public void addGroup(LogicalOperator operator, Consumer<FirstCondition> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        // Handle empty group case
        if (builder.getConditions().isEmpty() && builder.getGroups().isEmpty()) {
            return;  // Skip adding empty groups
        }

        // Create a new group with all conditions and nested groups
        List<SearchCondition.Node> nodes = new ArrayList<>();
        nodes.addAll(builder.getConditions());
        nodes.addAll(builder.getGroups());

        // Add the group with the specified operator
        this.condition.getNodes().add(new SearchCondition.Group(operator, nodes));
    }

    /**
     * Gets the DTO class type used for validation.
     *
     * @return the DTO class type, or null if no validation is configured
     */
    public Class<?> getDtoClass() {
        return dtoClass;
    }
}