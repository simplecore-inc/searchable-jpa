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
 * Enumeration of logical operators used in search conditions.
 * Provides support for AND and OR operations with JSON serialization/deserialization capabilities.
 *
 * <p>Each operator has an associated string name that is used for JSON representation:
 * <ul>
 *   <li>{@code AND} - represented as "and"</li>
 *   <li>{@code OR} - represented as "or"</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * LogicalOperator op = LogicalOperator.AND;
 * String name = op.getName(); // returns "and"
 *
 * // Parse from string
 * LogicalOperator parsed = LogicalOperator.fromName("or"); // returns LogicalOperator.OR
 * </pre>
 */
@Getter
@JsonSerialize(using = LogicalOperator.LogicalOperatorSerializer.class)
@JsonDeserialize(using = LogicalOperator.LogicalOperatorDeserializer.class)
public enum LogicalOperator {
    /**
     * Logical AND operator.
     * All conditions combined with this operator must be true for the overall condition to be true.
     */
    AND("and"),

    /**
     * Logical OR operator.
     * At least one of the conditions combined with this operator must be true for the overall condition to be true.
     */
    OR("or");

    /**
     * The string representation of the operator used in JSON.
     */
    private final String name;

    /**
     * Constructs a logical operator with the specified name.
     *
     * @param name the string representation of the operator
     */
    LogicalOperator(String name) {
        this.name = name;
    }

    /**
     * Finds a LogicalOperator by its string name (case-insensitive).
     *
     * @param operator the string name of the operator to find
     * @return the matching LogicalOperator, or null if no match is found
     */
    public static LogicalOperator fromName(String operator) {
        for (LogicalOperator op : values()) {
            if (op.getName().equalsIgnoreCase(operator)) {
                return op;
            }
        }
        return null;
    }

    /**
     * Custom JSON serializer for LogicalOperator.
     * Serializes the operator to its string name representation.
     */
    public static class LogicalOperatorSerializer extends JsonSerializer<LogicalOperator> {
        /**
         * Serializes a LogicalOperator to its string name.
         *
         * @param value    the LogicalOperator to serialize
         * @param gen      the JSON generator
         * @param provider the serializer provider
         * @throws IOException if an I/O error occurs during serialization
         */
        @Override
        public void serialize(LogicalOperator value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value != null ? value.getName() : null);
        }
    }

    /**
     * Custom JSON deserializer for LogicalOperator.
     * Deserializes a string name into its corresponding LogicalOperator.
     */
    public static class LogicalOperatorDeserializer extends JsonDeserializer<LogicalOperator> {
        /**
         * Deserializes a string into a LogicalOperator.
         *
         * @param p    the JSON parser
         * @param ctxt the deserialization context
         * @return the deserialized LogicalOperator, or null if the string doesn't match any operator
         * @throws IOException if an I/O error occurs during deserialization
         */
        @Override
        public LogicalOperator deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
            String value = p.getValueAsString();
            for (LogicalOperator op : LogicalOperator.values()) {
                if (op.getName().equalsIgnoreCase(value)) {
                    return op;
                }
            }
            return null;
        }
    }
} 