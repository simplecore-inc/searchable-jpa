package dev.simplecore.searchable.core.condition.builder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface defining the initial set of search conditions that can be applied.
 * This interface provides methods for creating basic search conditions using AND logic.
 * All methods return a {@link ChainedCondition} to support method chaining for building complex queries.
 *
 * <p>The conditions are grouped into several categories:
 * <ul>
 *   <li>Basic comparison operators (equals, not equals, greater than, less than)</li>
 *   <li>String pattern matching (contains, starts with, ends with)</li>
 *   <li>NULL checks (is null, is not null)</li>
 *   <li>Collection operations (in, not in)</li>
 *   <li>Range operations (between, not between)</li>
 * </ul>
 */
@SuppressWarnings({"UnusedReturnValue"})
public interface FirstCondition {
    /**
     * Creates a new condition group with AND logic.
     *
     * @param consumer consumer function to build the condition group
     * @return chained condition for further building
     */
    ChainedCondition where(Consumer<FirstCondition> consumer);

    /**
     * Adds a condition that checks if the field equals the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition equals(String field, Object value);

    /**
     * Adds a condition that checks if the field does not equal the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition notEquals(String field, Object value);

    /**
     * Adds a condition that checks if the field is greater than the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition greaterThan(String field, Object value);

    /**
     * Adds a condition that checks if the field is greater than or equal to the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition greaterThanOrEqualTo(String field, Object value);

    /**
     * Adds a condition that checks if the field is less than the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition lessThan(String field, Object value);

    /**
     * Adds a condition that checks if the field is less than or equal to the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return chained condition for further building
     */
    ChainedCondition lessThanOrEqualTo(String field, Object value);

    /**
     * Adds a condition that checks if the field contains the specified string value.
     *
     * @param field field name to check
     * @param value substring to search for
     * @return chained condition for further building
     */
    ChainedCondition contains(String field, String value);

    /**
     * Adds a condition that checks if the field does not contain the specified string value.
     *
     * @param field field name to check
     * @param value substring to search for
     * @return chained condition for further building
     */
    ChainedCondition notContains(String field, String value);

    /**
     * Adds a condition that checks if the field starts with the specified string value.
     *
     * @param field field name to check
     * @param value prefix to match
     * @return chained condition for further building
     */
    ChainedCondition startsWith(String field, String value);

    /**
     * Adds a condition that checks if the field does not start with the specified string value.
     *
     * @param field field name to check
     * @param value prefix to match
     * @return chained condition for further building
     */
    ChainedCondition notStartsWith(String field, String value);

    /**
     * Adds a condition that checks if the field ends with the specified string value.
     *
     * @param field field name to check
     * @param value suffix to match
     * @return chained condition for further building
     */
    ChainedCondition endsWith(String field, String value);

    /**
     * Adds a condition that checks if the field does not end with the specified string value.
     *
     * @param field field name to check
     * @param value suffix to match
     * @return chained condition for further building
     */
    ChainedCondition notEndsWith(String field, String value);

    /**
     * Adds a condition that checks if the field is null.
     *
     * @param field field name to check
     * @return chained condition for further building
     */
    ChainedCondition isNull(String field);

    /**
     * Adds a condition that checks if the field is not null.
     *
     * @param field field name to check
     * @return chained condition for further building
     */
    ChainedCondition isNotNull(String field);

    /**
     * Adds a condition that checks if the field value is in the specified list.
     *
     * @param field  field name to check
     * @param values list of values to match against
     * @return chained condition for further building
     */
    ChainedCondition in(String field, List<?> values);

    /**
     * Adds a condition that checks if the field value is not in the specified list.
     *
     * @param field  field name to check
     * @param values list of values to match against
     * @return chained condition for further building
     */
    ChainedCondition notIn(String field, List<?> values);

    /**
     * Adds a condition that checks if the field value is between the specified start and end values (inclusive).
     *
     * @param field field name to check
     * @param start start value of the range (inclusive)
     * @param end   end value of the range (inclusive)
     * @return chained condition for further building
     */
    ChainedCondition between(String field, Object start, Object end);

    /**
     * Adds a condition that checks if the field value is not between the specified start and end values (exclusive).
     *
     * @param field field name to check
     * @param start start value of the range (exclusive)
     * @param end   end value of the range (exclusive)
     * @return chained condition for further building
     */
    ChainedCondition notBetween(String field, Object start, Object end);
} 