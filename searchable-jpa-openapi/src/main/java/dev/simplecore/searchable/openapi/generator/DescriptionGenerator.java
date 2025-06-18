package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.models.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

public class DescriptionGenerator {
    private static final Logger log = LoggerFactory.getLogger(DescriptionGenerator.class);

    public static void customizeOperation(Operation operation, Class<?> dtoClass, String pattern, RequestType requestType) {
        // Add description
        String description = generateDescription(dtoClass, requestType, pattern);
        operation.setDescription(description);
    }

    public static String generateDescription(Class<?> dtoClass, RequestType requestType, String pattern) {
        log.debug("Generating OpenAPI description for DTO class: {}", dtoClass.getSimpleName());
        StringBuilder description = new StringBuilder();

        appendSearchFields(dtoClass, description);

        if (requestType == RequestType.GET) {
            appendGetRequestDocs(dtoClass, description, pattern);
        }

        return description.toString();
    }

    private static void appendSearchFields(Class<?> dtoClass, StringBuilder description) {
        description.append("### 🔍 Searchable Fields\n\n");
        description.append("| Field | Type | Description | Available Operators | Example |\n");
        description.append("|-------|------|-------------|-------------------|----------|\n");

        for (Field field : dtoClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(SearchableField.class)) {
                SearchableField searchOp = field.getAnnotation(SearchableField.class);
                Schema schema = field.getAnnotation(Schema.class);
                String fieldName = field.getName();
                String fieldDescription = schema != null && !schema.description().isEmpty() ?
                        schema.description() : fieldName;
                Object exampleValue = OpenApiDocUtils.getExampleValue(field);

                description.append("| ").append(fieldName).append(" | ");
                description.append(field.getType().getSimpleName()).append(" | ");
                description.append(fieldDescription).append(" | ");
                StringJoiner operationJoiner = new StringJoiner(", ");
                for (SearchOperator op : searchOp.operators()) {
                    operationJoiner.add(OpenApiDocUtils.getOperationDescription(op));
                }
                description.append(operationJoiner).append(" | ");
                description.append(exampleValue).append(" |\n");
            }
        }
        description.append("\n");
    }

    private static void appendGetRequestDocs(Class<?> dtoClass, StringBuilder description, String pattern) {
        appendSorting(dtoClass, description);
        appendPagination(description);
        appendExamples(dtoClass, description, pattern);
    }

    private static void appendSorting(Class<?> dtoClass, StringBuilder description) {
        description.append("### 📊 Sorting\n\n");
        description.append("| Field | Ascending | Descending |\n");
        description.append("|-------|-----------|------------|\n");

        for (Field field : dtoClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(SearchableField.class) &&
                    field.getAnnotation(SearchableField.class).sortable()) {
                String fieldName = field.getName();
                description.append("| ").append(fieldName).append(" | ");
                description.append("sort=").append(fieldName).append(".asc | ");
                description.append("sort=").append(fieldName).append(".desc |\n");
            }
        }
        description.append("\n");
        description.append("Multiple sort fields can be combined with commas: ");
        description.append("sort=name.asc,age.desc\n\n");
    }

    private static void appendPagination(StringBuilder description) {
        description.append("### 📄 Pagination\n\n");
        description.append("| Parameter | Description | Default | Example |\n");
        description.append("|-----------|-------------|---------|----------|\n");
        description.append("| page | Page number (0-based) | 0 | page=1 |\n");
        description.append("| size | Items per page | 10 | size=20 |\n\n");
    }

    private static void appendExamples(Class<?> dtoClass, StringBuilder description, String pattern) {
        description.append("### 🔍 Examples\n\n");
        description.append("| Type | Link |\n");
        description.append("|------|------|\n");

        // Simple example
        String simpleExample = generateUrlExample(dtoClass, false);
        description.append(String.format("| Simple Query | <a href='%s?%s' target='_blank'>Try Simple Query</a> |\n",
                pattern, simpleExample));

        // Complete example
        String completeExample = generateUrlExample(dtoClass, true);
        description.append(String.format("| Advanced Query | <a href='%s?%s' target='_blank'>Try Advanced Query</a> |\n\n",
                pattern, completeExample));
    }

    private static String generateUrlExample(Class<?> dtoClass, boolean isComplete) {
        StringBuilder example = new StringBuilder();
        boolean first = true;

        for (Field field : dtoClass.getDeclaredFields()) {
            SearchableField searchableField = field.getAnnotation(SearchableField.class);
            if (searchableField == null)
                continue;

            String fieldName = field.getName();
            if (!first) {
                example.append("&");
            }
            first = false;

            if (isComplete) {
                for (SearchOperator operator : searchableField.operators()) {
                    example.append("&");
                    appendOperationExample(example, field, operator, fieldName);
                }
                if (searchableField.sortable()) {
                    example.append(String.format("&sort=%s.asc,%s.desc", fieldName, fieldName));
                }
            } else {
                appendOperationExample(example, field, searchableField.operators()[0], fieldName);
                example.append("&page=0&size=10");
                break;
            }
        }

        return example.toString();
    }

    private static void appendOperationExample(StringBuilder example, Field field,
                                               SearchOperator op, String fieldName) {
        String paramName = String.format("%s.%s",
                fieldName,
                OpenApiDocUtils.toCamelCase(op.name().toLowerCase()));

        Object exampleValue = OpenApiDocUtils.getExampleValue(field, op);
        if (exampleValue == null) return;

        if (op == SearchOperator.IS_NULL || op == SearchOperator.IS_NOT_NULL) {
            example.append(String.format("%s=true", paramName));
            return;
        }

        if (op == SearchOperator.BETWEEN) {
            if (exampleValue instanceof List && ((List<?>) exampleValue).size() >= 2) {
                List<?> values = (List<?>) exampleValue;
                String formattedValue = values.get(0) + "," + values.get(1);
                example.append(String.format("%s=%s", paramName, formattedValue));
            }
        } else if (op == SearchOperator.IN || op == SearchOperator.NOT_IN) {
            if (exampleValue instanceof List) {
                String values = ((List<?>) exampleValue).stream()
                        .map(Object::toString)
                        .limit(2)
                        .collect(Collectors.joining(","));
                example.append(String.format("%s=%s", paramName, values));
            }
        } else if (exampleValue instanceof LocalDateTime) {
            example.append(String.format("%s=%s",
                    paramName,
                    ((LocalDateTime) exampleValue).format(DateTimeFormatter.ISO_DATE_TIME)));
        } else {
            example.append(String.format("%s=%s", paramName, exampleValue));
        }
    }

    public enum RequestType {
        GET, POST
    }
}