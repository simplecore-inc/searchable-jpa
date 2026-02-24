package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.models.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.StringJoiner;

public class DescriptionGenerator {
    private static final Logger log = LoggerFactory.getLogger(DescriptionGenerator.class);

    public static void customizeOperation(Operation operation, Class<?> dtoClass) {
        String description = generateDescription(dtoClass);
        operation.setDescription(description);
    }

    static String generateDescription(Class<?> dtoClass) {
        log.debug("Generating OpenAPI description for DTO class: {}", dtoClass.getSimpleName());
        StringBuilder description = new StringBuilder();
        appendSearchFieldSummary(dtoClass, description);
        return description.toString();
    }

    private static void appendSearchFieldSummary(Class<?> dtoClass, StringBuilder description) {
        description.append("**Searchable Fields:** ");

        StringJoiner fieldJoiner = new StringJoiner(", ");
        for (Field field : dtoClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(SearchableField.class)) {
                SearchableField searchOp = field.getAnnotation(SearchableField.class);
                StringJoiner opJoiner = new StringJoiner("|");
                for (SearchOperator op : searchOp.operators()) {
                    opJoiner.add(OpenApiDocUtils.toCamelCase(op.name().toLowerCase()));
                }
                String sortIndicator = searchOp.sortable() ? " [sortable]" : "";
                fieldJoiner.add(String.format("`%s` (%s)%s", field.getName(), opJoiner, sortIndicator));
            }
        }
        description.append(fieldJoiner);
    }
}
