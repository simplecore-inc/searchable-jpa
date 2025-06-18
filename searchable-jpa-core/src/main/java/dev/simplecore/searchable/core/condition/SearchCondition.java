package dev.simplecore.searchable.core.condition;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.simplecore.searchable.core.condition.jackson.SearchConditionDeserializer;
import dev.simplecore.searchable.core.condition.jackson.NodeDeserializer;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Core class representing a complete search condition with filtering, sorting, and pagination capabilities.
 * This class provides a structured way to define complex search criteria using a tree-like structure
 * of conditions and groups.
 *
 * <p>The search condition consists of:
 * <ul>
 *   <li>Search nodes: Individual conditions or groups of conditions connected by logical operators</li>
 *   <li>Sort criteria: Multiple field-based sort orders</li>
 *   <li>Pagination: Page number and size for result limiting</li>
 * </ul>
 *
 * <p>Example JSON representation:
 * <pre>
 * {
 *   "conditions": [
 *     {
 *       "operator": "and",
 *       "field": "name",
 *       "searchOperator": "equals",
 *       "value": "John"
 *     },
 *     {
 *       "operator": "or",
 *       "conditions": [
 *         {
 *           "field": "age",
 *           "searchOperator": "greaterThan",
 *           "value": 20
 *         }
 *       ]
 *     }
 *   ],
 *   "sort": {
 *     "orders": [
 *       {
 *         "field": "name",
 *         "direction": "asc"
 *       }
 *     ]
 *   },
 *   "page": 0,
 *   "size": 10
 * }
 * </pre>
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_EMPTY)
@Schema(description = "Search condition for filtering and pagination")
@JsonDeserialize(using = SearchConditionDeserializer.class)
public class SearchCondition<D> {

    /**
     * List of search condition nodes (conditions or groups).
     */
    @ArraySchema(
            arraySchema = @Schema(description = "List of search conditions or condition groups"),
            schema = @Schema(oneOf = {Condition.class, Group.class})
    )
    @JsonProperty("conditions")
    private final List<Node> nodes = new ArrayList<>();

    /**
     * Sort criteria for the search results.
     */
    @Setter
    @Schema(description = "Sort options")
    @JsonProperty("sort")
    private Sort sort;

    /**
     * Zero-based page number for pagination.
     */
    @Setter
    @Schema(description = "Page number (0-based)", example = "0")
    @JsonProperty("page")
    private Integer page;

    /**
     * Maximum number of results per page.
     */
    @Setter
    @Schema(description = "Page size", example = "10")
    @JsonProperty("size")
    private Integer size;

    /**
     * Default constructor for Jackson deserialization.
     */
    @JsonCreator
    public SearchCondition() {
    }

    /**
     * Creates a new search condition with the specified nodes, page, and size.
     *
     * @param nodes the list of search condition nodes
     * @param page  the page number
     * @param size  the page size
     */
    public SearchCondition(List<Node> nodes, Integer page, Integer size) {
        this.nodes.addAll(nodes);
        this.page = page;
        this.size = size;
    }


    /**
     * Creates a SearchCondition instance from a JSON string.
     *
     * @param json the JSON string to parse
     * @param dtoClass the class of the DTO type
     * @return a new SearchCondition instance
     * @throws JsonProcessingException if the JSON is invalid
     */
    public static <T> SearchCondition<T> fromJson(String json, Class<T> dtoClass) throws JsonProcessingException {
        ObjectMapper mapper = createObjectMapper();
        JavaType type = mapper.getTypeFactory().constructParametricType(SearchCondition.class, dtoClass);
        return mapper.readValue(json, type);
    }

    /**
     * Converts this search condition to a JSON string.
     *
     * @return JSON representation of this search condition
     * @throws JsonProcessingException if serialization fails
     */
    public String toJson() throws JsonProcessingException {
        return createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(this);
    }


    /**
     * Enumeration of sort directions with JSON serialization support.
     */
    @Getter
    @JsonSerialize(using = Direction.DirectionSerializer.class)
    @JsonDeserialize(using = Direction.DirectionDeserializer.class)
    public enum Direction {
        /**
         * Ascending sort order.
         */
        ASC("asc"),

        /**
         * Descending sort order.
         */
        DESC("desc");

        private final String name;

        Direction(String name) {
            this.name = name;
        }

        /**
         * Finds a Direction by its string name (case-insensitive).
         *
         * @param direction the string name to look up
         * @return matching Direction or null if not found
         */
        public static Direction fromName(String direction) {
            return Arrays.stream(values())
                    .filter(dir -> dir.getName().equalsIgnoreCase(direction))
                    .findFirst()
                    .orElse(null);
        }

        /**
         * Custom JSON serializer for Direction enum.
         */
        public static class DirectionSerializer extends JsonSerializer<Direction> {
            @Override
            public void serialize(Direction value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeString(value != null ? value.getName() : null);
            }
        }

        /**
         * Custom JSON deserializer for Direction enum.
         */
        public static class DirectionDeserializer extends JsonDeserializer<Direction> {
            @Override
            public Direction deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
                return Direction.fromName(p.getValueAsString());
            }
        }
    }

    /**
     * Interface representing a node in the search condition tree.
     * Can be either a Condition or a Group.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonDeserialize(using = NodeDeserializer.class)
    @Schema(description = "Base class for search condition nodes")
    public interface Node {
        LogicalOperator getOperator();

        void setOperator(LogicalOperator operator);

        // Default implementation for backward compatibility
        default List<Node> getNodes() {
            return Collections.emptyList();
        }
    }

    /**
     * Interface representing a leaf node (condition) in the search condition tree.
     */
    public interface ConditionNode extends Node {
        String getField();

        SearchOperator getSearchOperator();

        Object getValue();

        Object getValue2();

        String getEntityField();
    }

    /**
     * Interface representing a group node in the search condition tree.
     */
    public interface GroupNode extends Node {
        @Override
        List<Node> getNodes();
    }

    /**
     * Class representing a single search condition with a field, operator, and value(s).
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @RequiredArgsConstructor
    @Schema(description = "Single search condition")
    public static class Condition implements ConditionNode {
        /**
         * The field name to search on.
         */
        @JsonProperty("field")
        @Schema(description = "Field name to search on", example = "name")
        private final String field;
        /**
         * The search operator to apply.
         */
        @JsonProperty("searchOperator")
        @Schema(description = "Search operator", example = "EQUALS")
        private final SearchOperator searchOperator;
        /**
         * The primary search value.
         */
        @JsonProperty("value")
        @Schema(description = "Search value", example = "John")
        private final Object value;
        /**
         * The secondary search value.
         */
        @JsonProperty("value2")
        private final Object value2;
        /**
         * The entity field name (may differ from DTO field name).
         */
        @Setter
        @JsonProperty("entityField")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private String entityField;
        /**
         * The logical operator (AND/OR) for combining with other conditions.
         */
        @JsonProperty("operator")
        @Setter
        private LogicalOperator operator;

        /**
         * Default constructor for Jackson deserialization.
         */
        @JsonCreator
        public Condition(
                @JsonProperty("operator") LogicalOperator operator,
                @JsonProperty("field") String field,
                @JsonProperty("searchOperator") SearchOperator searchOperator,
                @JsonProperty("value") Object value,
                @JsonProperty("value2") Object value2,
                @JsonProperty("entityField") String entityField
        ) {
            this.operator = operator;
            this.field = field;
            this.searchOperator = searchOperator;
            this.value = value;
            this.value2 = value2;
            this.entityField = entityField;
        }

        @Override
        public LogicalOperator getOperator() {
            return operator;
        }

        @Override
        public String getField() {
            return field;
        }

        @Override
        public SearchOperator getSearchOperator() {
            return searchOperator;
        }

        @Override
        public Object getValue() {
            return value;
        }

        @Override
        public Object getValue2() {
            return value2;
        }

        @Override
        public String getEntityField() {
            return entityField;
        }
    }

    /**
     * Class representing a group of search conditions combined with a logical operator.
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @AllArgsConstructor
    @Schema(description = "Group of search conditions")
    public static class Group implements GroupNode {

        @JsonProperty("operator")
        @Schema(description = "Logical operator for the group", example = "AND")
        @Setter
        private LogicalOperator operator;

        @JsonProperty("conditions")
        @ArraySchema(
                arraySchema = @Schema(description = "List of nested conditions or groups"),
                schema = @Schema(oneOf = {Condition.class, Group.class})
        )
        private final List<Node> nodes;
        
        @JsonCreator
        public Group() {
            this(null, new ArrayList<>());
        }

        @Override
        public List<Node> getNodes() {
            return nodes;
        }
    }

    /**
     * Class representing sort criteria with multiple sort orders.
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @NoArgsConstructor
    @Schema(description = "Sort configuration")
    public static class Sort {
        /**
         * The list of sort orders.
         */
        @JsonProperty("orders")
        @NotNull(message = "{validator.sort.orders.required}")
        @Size(min = 1, message = "{validator.sort.orders.min}")
        private final List<Order> orders = new ArrayList<>();

        /**
         * Adds a sort order to this sort criteria.
         *
         * @param order the sort order to add
         */
        public void addOrder(Order order) {
            orders.add(order);
        }
    }

    /**
     * Class representing a single sort order with field and direction.
     */
    @Getter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @AllArgsConstructor
    public static class Order {
        @JsonProperty("field")
        @NotBlank(message = "{validator.sort.field.required}")
        @Schema(description = "Field name to sort by", example = "name")
        private final String field;

        @JsonProperty("direction")
        @NotNull(message = "{validator.sort.direction.required}")
        @JsonSerialize(using = Direction.DirectionSerializer.class)
        @JsonDeserialize(using = Direction.DirectionDeserializer.class)
        @Schema(description = "Sort direction", example = "ASC", allowableValues = {"ASC", "DESC"})
        private final Direction direction;

        @JsonProperty("entityField")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final String entityField;

        @JsonCreator
        public Order(
                @JsonProperty("field") String field,
                @JsonProperty("direction") Direction direction) {
            this(field, direction, null);
        }

        public boolean isAscending() {
            return Direction.ASC.equals(direction);
        }
    }

    /**
     * Creates an ObjectMapper configured for SearchCondition serialization/deserialization.
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        return mapper;
    }
}