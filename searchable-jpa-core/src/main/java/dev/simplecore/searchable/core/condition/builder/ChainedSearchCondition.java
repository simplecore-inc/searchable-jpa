package dev.simplecore.searchable.core.condition.builder;

import dev.simplecore.searchable.core.condition.SearchCondition;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Interface for building complete search conditions with support for filtering, sorting, and pagination.
 * This interface provides a fluent API for constructing search queries with various conditions.
 */
public interface ChainedSearchCondition<D> {
    /**
     * Adds a group of conditions combined with AND operator.
     *
     * @param consumer consumer function to build AND conditions
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> and(Consumer<FirstCondition> consumer);

    /**
     * Adds a group of conditions combined with OR operator.
     *
     * @param consumer consumer function to build OR conditions
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> or(Consumer<FirstCondition> consumer);

    /**
     * Adds sorting criteria to the search condition.
     * Multiple sort conditions can be added and will be applied in the order they are defined.
     *
     * @param consumer consumer function to build sort conditions
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer);

    /**
     * Sets the page number for pagination (zero-based).
     * For example, page(0) will return the first page of results.
     *
     * @param page zero-based page number
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> page(int page);

    /**
     * Sets the page size for pagination.
     * This determines the maximum number of results to return per page.
     *
     * @param size maximum number of results per page
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> size(int size);

    /**
     * Specifies entity fields to explicitly fetch join.
     * This is a server-side only feature for eagerly loading lazy relationships.
     *
     * @param fields the entity field paths to fetch (e.g., "author", "author.profile")
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> fetchFields(String... fields);

    /**
     * Specifies entity fields to explicitly fetch join.
     * This is a server-side only feature for eagerly loading lazy relationships.
     *
     * @param fields the set of entity field paths to fetch
     * @return this instance for method chaining
     */
    ChainedSearchCondition<D> fetchFields(Set<String> fields);

    /**
     * Builds and returns the final SearchCondition object.
     * This method should be called after all conditions, sorting, and pagination parameters have been set.
     *
     * @return the constructed SearchCondition object
     */
    SearchCondition<D> build();
}