package dev.simplecore.searchable.core.condition.jackson;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.exception.SearchableException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ValueDeserializer;

/**
 * Boot 4 adapter. Both Jackson generations use the same field mapping and validation
 * pipeline, preserving the public Jackson 2 API and the existing JSON wire format.
 */
public final class SearchConditionJackson3Deserializer extends ValueDeserializer<SearchCondition<?>> {
    private final Class<?> dtoClass;

    public SearchConditionJackson3Deserializer() {
        this(null);
    }

    private SearchConditionJackson3Deserializer(Class<?> dtoClass) {
        this.dtoClass = dtoClass;
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext context, BeanProperty property) {
        JavaType type = property == null ? context.getContextualType() : property.getType();
        if (type == null || type.containedTypeCount() == 0) {
            return context.reportInputMismatch(SearchCondition.class, "SearchCondition requires a DTO type");
        }
        return new SearchConditionJackson3Deserializer(type.containedType(0).getRawClass());
    }

    @Override
    public SearchCondition<?> deserialize(JsonParser parser, DeserializationContext context) {
        if (dtoClass == null) {
            return context.reportInputMismatch(SearchCondition.class, "SearchCondition requires a DTO type");
        }
        try {
            return SearchCondition.fromJson(context.readTree(parser).toString(), dtoClass);
        } catch (JsonProcessingException | SearchableException e) {
            return context.reportInputMismatch(SearchCondition.class, "Invalid search condition: %s", e.getMessage());
        }
    }
}
