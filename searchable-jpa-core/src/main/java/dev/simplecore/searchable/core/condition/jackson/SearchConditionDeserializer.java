package dev.simplecore.searchable.core.condition.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.SearchCondition.Sort;
import dev.simplecore.searchable.core.condition.validator.SearchConditionValidator;
import dev.simplecore.searchable.core.condition.validator.SearchRequestLimits;
import dev.simplecore.searchable.core.condition.validator.SearchableFieldValidator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchConditionDeserializer extends JsonDeserializer<SearchCondition<?>> implements ContextualDeserializer {
    
    private static final Logger log = LoggerFactory.getLogger(SearchConditionDeserializer.class);
    private Class<?> dtoClass;
    
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = (property == null) ? ctxt.getContextualType() : property.getType();
        if (type == null || type.containedTypeCount() == 0) {
            throw new IllegalStateException("Generic type information is not available. Make sure the SearchCondition is properly typed.");
        }
        
        SearchConditionDeserializer contextual = new SearchConditionDeserializer();
        contextual.dtoClass = type.containedType(0).getRawClass();
        return contextual;
    }
    
    @Override
    @SuppressWarnings({"unchecked"})
    public SearchCondition<?> deserialize(JsonParser p, DeserializationContext ctxt) 
            throws IOException {
        log.trace("SearchConditionDeserializer.deserialize called");
        
        ObjectMapper mapper = (ObjectMapper) p.getCodec();
        JsonNode jsonNode = mapper.readTree(p);
        
        SearchCondition<Object> searchCondition = new SearchCondition<>();
        
        // conditions
        if (jsonNode.has("conditions")) {
            JsonNode conditionsNode = jsonNode.get("conditions");
            if (!conditionsNode.isArray()) {
                throw new SearchableValidationException(
                        MessageUtils.getMessage("search.condition.conditions.not.array"));
            }
            if (conditionsNode.size() > 1000) throw new SearchableValidationException("Search exceeds maximum node count 1000");
            for (JsonNode conditionNode : conditionsNode) {
                NodeDeserializer nodeDeserializer = new NodeDeserializer();
                nodeDeserializer.setDtoClass(dtoClass);

                JsonParser conditionParser = conditionNode.traverse(mapper);
                Node node = nodeDeserializer.deserialize(conditionParser, ctxt);
                searchCondition.getNodes().add(node);
            }
        }
        
        // sort
        if (jsonNode.has("sort")) {
            searchCondition.setSort(
                mapper.treeToValue(jsonNode.get("sort"), Sort.class)
            );
        }
        
        // pagination
        if (jsonNode.has("page")) {
            searchCondition.setPage(readInteger(jsonNode.get("page"), "page"));
        }
        if (jsonNode.has("size")) {
            searchCondition.setSize(readInteger(jsonNode.get("size"), "size"));
        }

        if (searchCondition.getSort() != null) {
            Sort mapped = new Sort();
            for (SearchCondition.Order order : searchCondition.getSort().getOrders()) {
                if (order == null || order.getDirection() == null) {
                    throw new SearchableValidationException("Sort direction must be asc or desc");
                }
                mapped.addOrder(new SearchCondition.Order(order.getField(), order.getDirection(),
                        SearchableFieldUtils.getSortFieldFromDto(dtoClass, order.getField())));
            }
            searchCondition.setSort(mapped);
        }

        SearchRequestLimits.validateStructure(searchCondition);

        // Only validate if DTO class is available
        if (dtoClass != null) {
            log.trace("Using DTO class in deserializer: {}", dtoClass.getName());
            Class<Object> typedDtoClass = (Class<Object>) dtoClass;
            
            // First validate @SearchableField annotations
            new SearchableFieldValidator<>(typedDtoClass, searchCondition).validate();

            // Then validate constraints
            new SearchConditionValidator<>(typedDtoClass, searchCondition).validate();

            // Set entityField
            for (Node node : searchCondition.getNodes()) {
                setEntityFieldsRecursively(node, dtoClass);
            }
        }
        
        return searchCondition;
    }

    private void setEntityFieldsRecursively(Node node, Class<?> dtoClass) {
        if (node instanceof Condition) {
            Condition condition = (Condition) node;
            String fieldName = condition.getField();
            
            condition.setEntityField(SearchableFieldUtils.getEntityFieldFromDto(dtoClass, fieldName));
        } else if (node instanceof Group) {
            Group group = (Group) node;
            for (Node childNode : group.getNodes()) {
                setEntityFieldsRecursively(childNode, dtoClass);
            }
        }
    }
    private Integer readInteger(JsonNode node, String name) {
        if (node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw new SearchableValidationException(name + " must be a 32-bit integer");
        }
        return node.intValue();
    }
} 