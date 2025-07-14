package dev.simplecore.searchable.core.condition.builder;


import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;


/**
 * Builder class for constructing sort criteria in search conditions.
 * This class provides a fluent API for defining the sort order of search results
 * using multiple fields with ascending or descending directions.
 *
 * <p>The builder automatically maps DTO field names to entity field names using
 * the {@link SearchableFieldUtils} utility class.
 *
 * <p>Note: Only fields annotated with {@code @SearchableField(sortable = true)}
 * can be used for sorting.
 */
@SuppressWarnings({"UnusedReturnValue"})
public class SortBuilder {
    /**
     * The sort instance being built, containing the ordered list of sort criteria.
     */
    private final SearchCondition.Sort sort;

    /**
     * The DTO class type used for field name resolution.
     */
    private final Class<?> dtoClass;

    /**
     * Creates a new SortBuilder for the specified sort criteria and DTO class.
     *
     * @param sort     the sort instance to build
     * @param dtoClass the DTO class type used for field name resolution
     */
    public SortBuilder(SearchCondition.Sort sort, Class<?> dtoClass) {
        this.sort = sort;
        this.dtoClass = dtoClass;
    }

    /**
     * Adds an ascending sort order for the specified field.
     * The field name is resolved to its corresponding entity field name using the DTO class.
     * First checks for sortField attribute, then falls back to entityField or the field name itself.
     *
     * @param field the field name to sort by
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if the field is not marked as sortable
     */
    public SortBuilder asc(String field) {
        String entityField = SearchableFieldUtils.getSortFieldFromDto(dtoClass, field);
        sort.addOrder(new SearchCondition.Order(field, SearchCondition.Direction.ASC, entityField));
        return this;
    }

    /**
     * Adds a descending sort order for the specified field.
     * The field name is resolved to its corresponding entity field name using the DTO class.
     * First checks for sortField attribute, then falls back to entityField or the field name itself.
     *
     * @param field the field name to sort by
     * @return this builder instance for method chaining
     * @throws IllegalArgumentException if the field is not marked as sortable
     */
    @SuppressWarnings({"UnusedReturnValue"})
    public SortBuilder desc(String field) {
        String entityField = SearchableFieldUtils.getSortFieldFromDto(dtoClass, field);
        sort.addOrder(new SearchCondition.Order(field, SearchCondition.Direction.DESC, entityField));
        return this;
    }
}