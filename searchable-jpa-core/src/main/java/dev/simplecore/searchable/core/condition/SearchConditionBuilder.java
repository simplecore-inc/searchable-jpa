package dev.simplecore.searchable.core.condition;

import dev.simplecore.searchable.core.condition.builder.*;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.validator.SearchConditionValidator;
import dev.simplecore.searchable.core.condition.validator.SearchableFieldValidator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
     * Creates a new builder initialized with an existing SearchCondition.
     * This allows adding additional conditions to an existing search condition
     * while maintaining immutability of the original.
     *
     * <p>Example usage:
     * <pre>{@code
     * SearchCondition<UserDTO> base = SearchConditionBuilder.create(UserDTO.class)
     *     .where(w -> w.equals("status", "ACTIVE"))
     *     .build();
     *
     * SearchCondition<UserDTO> extended = SearchConditionBuilder.from(base, UserDTO.class)
     *     .and(a -> a.equals("tenantId", currentTenantId))
     *     .build();
     * }</pre>
     *
     * @param <D>       the type of the DTO class
     * @param existing  the existing SearchCondition to copy from
     * @param dtoClass  the DTO class type to validate against
     * @return a new SearchConditionBuilder initialized with the existing condition's data
     * @throws SearchableValidationException if existing is null
     * @throws SearchableValidationException if dtoClass is null
     */
    public static <D> SearchConditionBuilder<D> from(SearchCondition<D> existing, Class<D> dtoClass) {
        if (existing == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("builder.existing.condition.required"));
        }
        return new SearchConditionBuilder<>(existing, dtoClass);
    }

    /**
     * Creates a new builder instance initialized with an existing SearchCondition.
     * Copies all nodes, sort criteria, page, and size from the existing condition.
     *
     * @param existing the existing SearchCondition to copy from
     * @param dtoClass the DTO class type to validate against
     * @throws SearchableValidationException if dtoClass is null
     */
    private SearchConditionBuilder(SearchCondition<D> existing, Class<D> dtoClass) {
        if (dtoClass == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("builder.dto.class.required"));
        }
        this.dtoClass = dtoClass;
        this.condition = new SearchCondition<>();

        // Deep-copy nodes so mutating the extended condition never affects the original.
        for (SearchCondition.Node node : existing.getNodes()) {
            this.condition.getNodes().add(deepCopyNode(node));
        }

        // Copy pagination
        this.condition.setPage(existing.getPage());
        this.condition.setSize(existing.getSize());

        // Copy sort (create new Sort instance)
        if (existing.getSort() != null) {
            SearchCondition.Sort newSort = new SearchCondition.Sort();
            for (SearchCondition.Order order : existing.getSort().getOrders()) {
                newSort.addOrder(new SearchCondition.Order(
                        order.getField(),
                        order.getDirection(),
                        order.getEntityField()
                ));
            }
            this.condition.setSort(newSort);
        }

        // Copy fetchFields
        if (existing.getFetchFields() != null && !existing.getFetchFields().isEmpty()) {
            this.condition.setFetchFields(new HashSet<>(existing.getFetchFields()));
        }
    }

    /**
     * Starts building the search condition with an initial group of conditions.
     * It handles both direct conditions and nested groups, preserving the order in which they were
     * declared. This method may also be called more than once; each call appends its conditions.
     *
     * @param consumer a consumer that configures the initial conditions
     * @return a chained builder for additional configuration
     */
    public SearchConditionBuilder<D> where(Consumer<ConditionGroupBuilder> consumer) {
        ConditionGroupBuilder builder = new ConditionGroupBuilder(dtoClass);
        consumer.accept(builder);

        condition.getNodes().addAll(builder.getNodes());

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
        combineGroup(builder.getNodes(), LogicalOperator.AND);
        return this;
    }

    /**
     * Combines a sub-builder's ordered nodes into the top-level condition. A flat set of conditions
     * (no nested groups) is inlined with the given operator applied to the first (operator-less) node,
     * preserving any {@code orXXX()} operators; otherwise the whole set is wrapped in a single group so
     * its internal order is preserved.
     */
    private void combineGroup(List<SearchCondition.Node> subNodes, LogicalOperator operator) {
        if (subNodes.isEmpty()) {
            return;
        }

        boolean hasNestedGroup = subNodes.stream().anyMatch(node -> node instanceof SearchCondition.Group);
        if (!hasNestedGroup) {
            for (SearchCondition.Node node : subNodes) {
                SearchCondition.Condition condition = (SearchCondition.Condition) node;
                // Only set the operator when it is null (preserve orXXX()/other operators).
                if (condition.getOperator() == null) {
                    condition.setOperator(operator);
                }
                this.condition.getNodes().add(condition);
            }
        } else {
            this.condition.getNodes().add(new SearchCondition.Group(operator, subNodes));
        }
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
        combineGroup(builder.getNodes(), LogicalOperator.OR);
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
     * Specifies entity fields to explicitly fetch join.
     * This is a server-side only feature for eagerly loading lazy relationships.
     *
     * <p>Example usage:
     * <pre>{@code
     * SearchCondition<PostDTO> condition = SearchConditionBuilder.create(PostDTO.class)
     *     .where(w -> w.equals("status", "PUBLISHED"))
     *     .fetchFields("author", "author.profile", "category")
     *     .build();
     * }</pre>
     *
     * @param fields the entity field paths to fetch (e.g., "author", "author.profile")
     * @return a chained builder for additional configuration
     */
    public ChainedSearchCondition<D> fetchFields(String... fields) {
        condition.setFetchFields(new HashSet<>(Arrays.asList(fields)));
        return new ChainedSearchConditionImpl<>(this);
    }

    /**
     * Specifies entity fields to explicitly fetch join.
     * This is a server-side only feature for eagerly loading lazy relationships.
     *
     * @param fields the set of entity field paths to fetch
     * @return a chained builder for additional configuration
     */
    public ChainedSearchCondition<D> fetchFields(Set<String> fields) {
        condition.setFetchFields(new HashSet<>(fields));
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

        // Skip adding empty groups
        if (builder.getNodes().isEmpty()) {
            return;
        }

        // Add the group (preserving the sub-builder's node order) with the specified operator
        this.condition.getNodes().add(new SearchCondition.Group(operator, builder.getNodes()));
    }

    /**
     * Deep-copies a node so that mutating the copy (e.g. via {@code setOperator}) never affects the
     * original. Conditions are copied field-by-field; groups are copied recursively.
     *
     * @param node the node to copy
     * @return an independent copy of the node
     */
    private static SearchCondition.Node deepCopyNode(SearchCondition.Node node) {
        if (node instanceof SearchCondition.Condition) {
            SearchCondition.Condition source = (SearchCondition.Condition) node;
            return new SearchCondition.Condition(
                    source.getOperator(),
                    source.getField(),
                    source.getSearchOperator(),
                    source.getValue(),
                    source.getValue2(),
                    source.getEntityField());
        }
        if (node instanceof SearchCondition.Group) {
            SearchCondition.Group source = (SearchCondition.Group) node;
            List<SearchCondition.Node> copies = new ArrayList<>();
            for (SearchCondition.Node child : source.getNodes()) {
                copies.add(deepCopyNode(child));
            }
            return new SearchCondition.Group(source.getOperator(), copies);
        }
        return node;
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