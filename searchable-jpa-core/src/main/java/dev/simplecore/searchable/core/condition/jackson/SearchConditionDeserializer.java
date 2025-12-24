package dev.simplecore.searchable.core.condition.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.BeanProperty;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchCondition.Node;
import dev.simplecore.searchable.core.condition.SearchCondition.Sort;
import dev.simplecore.searchable.core.condition.validator.SearchConditionValidator;

import dev.simplecore.searchable.core.condition.validator.SearchableFieldValidator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;
import dev.simplecore.searchable.core.condition.SearchCondition.Condition;
import dev.simplecore.searchable.core.condition.SearchCondition.Group;
import dev.simplecore.searchable.core.annotation.SearchableField;

public class SearchConditionDeserializer extends JsonDeserializer<SearchCondition<?>> implements ContextualDeserializer {
    
    private static final Logger log = LoggerFactory.getLogger(SearchConditionDeserializer.class);
    private Class<?> dtoClass;
    
    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        JavaType type = (property == null) ? ctxt.getContextualType() : property.getType();
        if (type == null || type.containedTypeCount() == 0) {
            throw new IllegalStateException("Generic type information is not available. Make sure the SearchCondition is properly typed.");
        }
        
        JavaType dtoType = type.containedType(0);
        this.dtoClass = dtoType.getRawClass();
        log.info("Found DTO class in createContextual: {}", dtoClass.getName());

        try {
            JsonDeserializer<?> deserializer = ctxt.findRootValueDeserializer(
                ctxt.constructType(Node.class)
            );
            if (deserializer instanceof NodeDeserializer) {
                ((NodeDeserializer) deserializer).setDtoClass(dtoClass);
            }
        } catch (Exception e) {
            log.warn("Failed to set DTO class in NodeDeserializer", e);
        }
        
        return this;
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
            if (conditionsNode.isArray()) {
                for (JsonNode conditionNode : conditionsNode) {
                    NodeDeserializer nodeDeserializer = new NodeDeserializer();
                    nodeDeserializer.setDtoClass(dtoClass);

                    JsonParser conditionParser = conditionNode.traverse(mapper);
                    Node node = nodeDeserializer.deserialize(conditionParser, ctxt);
                    searchCondition.getNodes().add(node);
                }
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
            searchCondition.setPage(jsonNode.get("page").asInt());
        }
        if (jsonNode.has("size")) {
            searchCondition.setSize(jsonNode.get("size").asInt());
        }

        // Only validate if DTO class is available
        if (dtoClass != null) {
            log.info("Using DTO class in deserializer: {}", dtoClass.getName());
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
            
            try {
                java.lang.reflect.Field field = dtoClass.getDeclaredField(fieldName);
                SearchableField annotation = field.getAnnotation(SearchableField.class);
                
                if (annotation != null && !annotation.entityField().isEmpty()) {
                    condition.setEntityField(annotation.entityField());
                } else {
                    condition.setEntityField(fieldName);
                }
            } catch (NoSuchFieldException e) {
                log.warn("Field {} not found in {}", fieldName, dtoClass.getName());
                condition.setEntityField(fieldName);
            }
        } else if (node instanceof Group) {
            Group group = (Group) node;
            for (Node childNode : group.getNodes()) {
                setEntityFieldsRecursively(childNode, dtoClass);
            }
        }
    }
} 