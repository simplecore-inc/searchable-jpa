package dev.simplecore.searchable.core.condition.operator;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.Getter;

import java.io.IOException;

/**
 * Enumeration of search operators used for building search conditions.
 * Provides a comprehensive set of operators for comparing values, pattern matching,
 * null checks, collection operations, and range queries.
 *
 * <p>The operators are grouped into several categories:
 * <ul>
 *   <li>Comparison operators (equals, not equals, greater than, less than)</li>
 *   <li>LIKE operators for string pattern matching (contains, starts with, ends with)</li>
 *   <li>NULL checks (is null, is not null)</li>
 *   <li>Collection operations (in, not in)</li>
 *   <li>Range operations (between, not between)</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * SearchOperator op = SearchOperator.EQUALS;
 * String name = op.getName(); // returns "equals"
 *
 * // Parse from string
 * SearchOperator parsed = SearchOperator.fromName("contains"); // returns SearchOperator.CONTAINS
 * </pre>
 */
@Getter
@JsonSerialize(using = SearchOperator.SearchOperatorSerializer.class)
@JsonDeserialize(using = SearchOperator.SearchOperatorDeserializer.class)
public enum SearchOperator {
    // Comparison operators
    /**
     * Checks if a field equals a specified value.
     */
    EQUALS("equals"),

    /**
     * Checks if a field does not equal a specified value.
     */
    NOT_EQUALS("notEquals"),

    /**
     * Checks if a field is greater than a specified value.
     */
    GREATER_THAN("greaterThan"),

    /**
     * Checks if a field is greater than or equal to a specified value.
     */
    GREATER_THAN_OR_EQUAL_TO("greaterThanOrEqualTo"),

    /**
     * Checks if a field is less than a specified value.
     */
    LESS_THAN("lessThan"),

    /**
     * Checks if a field is less than or equal to a specified value.
     */
    LESS_THAN_OR_EQUAL_TO("lessThanOrEqualTo"),

    // LIKE operators
    /**
     * Checks if a string field contains a specified substring.
     */
    CONTAINS("contains"),

    /**
     * Checks if a string field does not contain a specified substring.
     */
    NOT_CONTAINS("notContains"),

    /**
     * Checks if a string field starts with a specified prefix.
     */
    STARTS_WITH("startsWith"),

    /**
     * Checks if a string field does not start with a specified prefix.
     */
    NOT_STARTS_WITH("notStartsWith"),

    /**
     * Checks if a string field ends with a specified suffix.
     */
    ENDS_WITH("endsWith"),

    /**
     * Checks if a string field does not end with a specified suffix.
     */
    NOT_ENDS_WITH("notEndsWith"),

    // NULL checks
    /**
     * Checks if a field is null.
     */
    IS_NULL("isNull"),

    /**
     * Checks if a field is not null.
     */
    IS_NOT_NULL("isNotNull"),

    // IN operators
    /**
     * Checks if a field's value is in a specified list of values.
     */
    IN("in"),

    /**
     * Checks if a field's value is not in a specified list of values.
     */
    NOT_IN("notIn"),

    // BETWEEN operators
    /**
     * Checks if a field's value is between two specified values (inclusive).
     */
    BETWEEN("between"),

    /**
     * Checks if a field's value is not between two specified values (exclusive).
     */
    NOT_BETWEEN("notBetween");

    /**
     * The string representation of the operator used in JSON.
     */
    private final String name;

    /**
     * Constructs a search operator with the specified name.
     *
     * @param name the string representation of the operator
     */
    SearchOperator(String name) {
        this.name = name;
    }

    /**
     * Finds a SearchOperator by its string name (case-insensitive).
     *
     * @param operator the string name of the operator to find
     * @return the matching SearchOperator, or null if no match is found
     */
    public static SearchOperator fromName(String operator) {
        for (SearchOperator op : values()) {
            if (op.getName().equalsIgnoreCase(operator)) {
                return op;
            }
        }
        return null;
    }

    /**
     * Custom JSON serializer for SearchOperator.
     * Serializes the operator to its string name representation.
     */
    public static class SearchOperatorSerializer extends JsonSerializer<SearchOperator> {
        /**
         * Serializes a SearchOperator to its string name.
         *
         * @param value    the SearchOperator to serialize
         * @param gen      the JSON generator
         * @param provider the serializer provider
         * @throws IOException if an I/O error occurs during serialization
         */
        @Override
        public void serialize(SearchOperator value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value != null ? value.getName() : null);
        }
    }

    /**
     * Custom JSON deserializer for SearchOperator.
     * Deserializes a string name into its corresponding SearchOperator.
     */
    public static class SearchOperatorDeserializer extends JsonDeserializer<SearchOperator> {
        /**
         * Deserializes a string into a SearchOperator.
         *
         * @param p    the JSON parser
         * @param ctxt the deserialization context
         * @return the deserialized SearchOperator, or null if the string doesn't match any operator
         * @throws IOException if an I/O error occurs during deserialization
         */
        @Override
        public SearchOperator deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            SearchOperator operator = SearchOperator.fromName(value);
            if (operator == null) {
                throw new IllegalArgumentException("No enum constant " + SearchOperator.class.getName() + "." + value);
            }
            return operator;
        }
    }
} 