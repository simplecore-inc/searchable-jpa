package dev.simplecore.searchable.core.condition.builder;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;

import java.util.function.Consumer;

/**
 * Implementation of {@link ChainedSearchCondition} interface that delegates operations to a {@link SearchConditionBuilder}.
 * This class provides the concrete implementation of the fluent API for building search conditions.
 *
 * <p>This implementation uses the builder pattern internally to construct the search condition,
 * while maintaining a fluent interface for method chaining.
 */
public class ChainedSearchConditionImpl<D> implements ChainedSearchCondition<D> {
    /**
     * The underlying builder instance that handles the actual construction of the search condition.
     */
    private final SearchConditionBuilder<D> builder;

    /**
     * Constructs a new ChainedSearchConditionImpl with the specified builder.
     *
     * @param builder the SearchConditionBuilder instance to delegate operations to
     */
    public ChainedSearchConditionImpl(SearchConditionBuilder<D> builder) {
        this.builder = builder;
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to add an AND group of conditions.
     */
    @Override
    public ChainedSearchCondition<D> and(Consumer<FirstCondition> consumer) {
        builder.addGroup(LogicalOperator.AND, consumer);
        return this;
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to add an OR group of conditions.
     */
    @Override
    public ChainedSearchCondition<D> or(Consumer<FirstCondition> consumer) {
        builder.addGroup(LogicalOperator.OR, consumer);
        return this;
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to add sorting criteria.
     */
    @Override
    public ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer) {
        builder.sort(consumer);
        return this;
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to set the page number.
     */
    @Override
    public ChainedSearchCondition<D> page(int page) {
        return builder.page(page);
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to set the page size.
     */
    @Override
    public ChainedSearchCondition<D> size(int size) {
        return builder.size(size);
    }

    /**
     * {@inheritDoc}
     * Delegates to the underlying builder to construct the final SearchCondition object.
     */
    @Override
    public SearchCondition<D> build() {
        return builder.build();
    }
}