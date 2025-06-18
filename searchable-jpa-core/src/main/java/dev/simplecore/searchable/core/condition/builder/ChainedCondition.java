package dev.simplecore.searchable.core.condition.builder;

import java.util.List;
import java.util.function.Consumer;

/**
 * Interface for building chained search conditions with OR operations.
 * Extends {@link FirstCondition} to include all basic AND operations while providing additional OR operations.
 * This interface enables fluent API style condition building with method chaining.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public interface ChainedCondition extends FirstCondition {
    /**
     * Creates a new AND group with nested conditions.
     *
     * @param consumer consumer function to build nested conditions
     * @return this instance for method chaining
     */
    ChainedCondition and(Consumer<FirstCondition> consumer);

    /**
     * Creates a new OR group with nested conditions.
     *
     * @param consumer consumer function to build nested conditions
     * @return this instance for method chaining
     */
    ChainedCondition or(Consumer<FirstCondition> consumer);

    /**
     * Adds an OR condition that checks if the field equals the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orEquals(String field, Object value);

    /**
     * Adds an OR condition that checks if the field does not equal the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orNotEquals(String field, Object value);

    /**
     * Adds an OR condition that checks if the field is greater than the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orGreaterThan(String field, Object value);

    /**
     * Adds an OR condition that checks if the field is greater than or equal to the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orGreaterThanOrEqualTo(String field, Object value);

    /**
     * Adds an OR condition that checks if the field is less than the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orLessThan(String field, Object value);

    /**
     * Adds an OR condition that checks if the field is less than or equal to the specified value.
     *
     * @param field field name to compare
     * @param value value to compare against
     * @return this instance for method chaining
     */
    ChainedCondition orLessThanOrEqualTo(String field, Object value);

    /**
     * Adds an OR condition that checks if the field contains the specified string value.
     *
     * @param field field name to check
     * @param value substring to search for
     * @return this instance for method chaining
     */
    ChainedCondition orContains(String field, String value);

    /**
     * Adds an OR condition that checks if the field does not contain the specified string value.
     *
     * @param field field name to check
     * @param value substring to search for
     * @return this instance for method chaining
     */
    ChainedCondition orNotContains(String field, String value);

    /**
     * Adds an OR condition that checks if the field starts with the specified string value.
     *
     * @param field field name to check
     * @param value prefix to match
     * @return this instance for method chaining
     */
    ChainedCondition orStartsWith(String field, String value);

    /**
     * Adds an OR condition that checks if the field does not start with the specified string value.
     *
     * @param field field name to check
     * @param value prefix to match
     * @return this instance for method chaining
     */
    ChainedCondition orNotStartsWith(String field, String value);

    /**
     * Adds an OR condition that checks if the field ends with the specified string value.
     *
     * @param field field name to check
     * @param value suffix to match
     * @return this instance for method chaining
     */
    ChainedCondition orEndsWith(String field, String value);

    /**
     * Adds an OR condition that checks if the field does not end with the specified string value.
     *
     * @param field field name to check
     * @param value suffix to match
     * @return this instance for method chaining
     */
    ChainedCondition orNotEndsWith(String field, String value);

    /**
     * Adds an OR condition that checks if the field is null.
     *
     * @param field field name to check
     * @return this instance for method chaining
     */
    ChainedCondition orIsNull(String field);

    /**
     * Adds an OR condition that checks if the field is not null.
     *
     * @param field field name to check
     * @return this instance for method chaining
     */
    ChainedCondition orIsNotNull(String field);

    /**
     * Adds an OR condition that checks if the field value is in the specified list.
     *
     * @param field  field name to check
     * @param values list of values to match against
     * @return this instance for method chaining
     */
    ChainedCondition orIn(String field, List<?> values);

    /**
     * Adds an OR condition that checks if the field value is not in the specified list.
     *
     * @param field  field name to check
     * @param values list of values to match against
     * @return this instance for method chaining
     */
    ChainedCondition orNotIn(String field, List<?> values);

    /**
     * Adds an OR condition that checks if the field value is between the specified start and end values (inclusive).
     *
     * @param field field name to check
     * @param start start value of the range (inclusive)
     * @param end   end value of the range (inclusive)
     * @return this instance for method chaining
     */
    ChainedCondition orBetween(String field, Object start, Object end);

    /**
     * Adds an OR condition that checks if the field value is not between the specified start and end values (exclusive).
     *
     * @param field field name to check
     * @param start start value of the range (exclusive)
     * @param end   end value of the range (exclusive)
     * @return this instance for method chaining
     */
    ChainedCondition orNotBetween(String field, Object start, Object end);
} 