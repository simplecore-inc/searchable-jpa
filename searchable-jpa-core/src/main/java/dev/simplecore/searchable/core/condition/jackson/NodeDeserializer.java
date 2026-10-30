package dev.simplecore.searchable.core.condition.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Setter;

/**
 * Custom deserializer for Node interface implementations.
 * Handles deserialization of both Condition and Group nodes.
 */
@Setter
public class NodeDeserializer extends JsonDeserializer<Node> {

    @SuppressWarnings("unused")
    private Class<?> dtoClass;

    @JsonCreator
    public NodeDeserializer() {
    }

    @Override
    public Node deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode node = mapper.readTree(p);

        checkTree(node, 0, new int[1]);
        return node.has("conditions") ? deserializeGroup(node, mapper) : deserializeCondition(node);
    }

    private Group deserializeGroup(JsonNode node, ObjectMapper mapper) throws IOException {
        LogicalOperator operator = getLogicalOperator(node);
        List<Node> nodes = new ArrayList<>();

        JsonNode conditions = node.get("conditions");
        if (!conditions.isArray()) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("search.condition.conditions.not.array"));
        }
        for (JsonNode condition : conditions) {
            Node deserializedNode = condition.has("conditions") ?
                    mapper.treeToValue(condition, Group.class) :
                    mapper.treeToValue(condition, Condition.class);

            if (deserializedNode instanceof Group) {
                Group group = (Group) deserializedNode;
                if (group.getNodes().size() == 1) {
                    Node child = group.getNodes().get(0);
                    child.setOperator(group.getOperator());
                    nodes.add(child);
                } else {
                    nodes.add(group);
                }
            } else {
                nodes.add(deserializedNode);
            }
        }

        return new Group(operator, nodes);
    }

    private Node deserializeCondition(JsonNode node) {
        if (!node.has("field") || node.get("field").isNull()) {
            throw new SearchableValidationException(MessageUtils.getMessage("search.condition.field.required"));
        }
        if (!node.has("searchOperator") || node.get("searchOperator").isNull()) {
            throw new SearchableValidationException(MessageUtils.getMessage("search.condition.operator.required"));
        }

        // Use the null-safe fromName() (like group parsing) and reject an explicitly-provided
        // but unknown logical operator with a proper validation exception.
        LogicalOperator operator = getLogicalOperator(node);

        String field = node.get("field").asText();
        SearchOperator searchOperator = SearchOperator.fromName(node.get("searchOperator").asText());
        String entityField = node.has("entityField") ? node.get("entityField").asText() : null;

        if (searchOperator == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.supported",
                new Object[]{node.get("searchOperator").asText()}));
        }

        Object value = getNodeValue(node.has("value") ? node.get("value") : null);
        Object value2 = getNodeValue(node.has("value2") ? node.get("value2") : null);

        return new Condition(operator, field, searchOperator, value, value2, entityField);
    }

    private Object getNodeValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        // Preserve array values (e.g. for IN/NOT_IN) instead of collapsing them to an empty string.
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            for (JsonNode element : node) {
                values.add(element.isNull() ? null : element.asText());
            }
            return values;
        }
        // Preserve object values as their JSON text rather than an empty string.
        if (node.isObject()) {
            return node.toString();
        }
        return node.asText();
    }

    private LogicalOperator getLogicalOperator(JsonNode node) {
        if (!node.hasNonNull("operator")) return null;
        String text = node.get("operator").asText();
        LogicalOperator operator = LogicalOperator.fromName(text);
        if (operator == null) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("search.condition.operator.invalid", new Object[]{text}));
        }
        return operator;
    }

    private void checkTree(JsonNode node, int depth, int[] count) {
        if (depth > SearchRequestLimits.MAX_DEPTH || ++count[0] > SearchRequestLimits.MAX_NODES) {
            throw new SearchableValidationException("Search tree exceeds the depth or node limit");
        }
        if (node.path("value").isArray() && node.path("value").size() > SearchRequestLimits.MAX_IN_VALUES) {
            throw new SearchableValidationException("Search collection exceeds maximum size 1000");
        }
        if (node.path("conditions").isArray()) {
            for (JsonNode child : node.path("conditions")) checkTree(child, depth + 1, count);
        }
    }
} 