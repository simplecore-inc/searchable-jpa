package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.RequiredArgsConstructor;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ParameterGenerator {
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);

    public void customizeParameters(Operation operation, Class<?> dtoClass) {
        if (operation.getParameters() == null) {
            operation.setParameters(new ArrayList<>());
        }

        // Add search field parameters
        addSearchFieldParameters(operation, dtoClass);

        // Add pagination parameters
        addPaginationParameters(operation);

        // Add sorting parameters
        addSortingParameters(operation, dtoClass);
    }

    private void addSearchFieldParameters(Operation operation, Class<?> dtoClass) {
        for (Field field : dtoClass.getDeclaredFields()) {
            SearchableField searchableField = field.getAnnotation(SearchableField.class);
            if (searchableField == null) continue;

            String fieldName = field.getName();
            String fieldDescription = getFieldDescription(field);

            for (SearchOperator operator : searchableField.operators()) {
                String paramName = String.format("%s.%s",
                        fieldName,
                        OpenApiDocUtils.toCamelCase(operator.name().toLowerCase()));

                Schema<?> schema = createFieldSchema(field, operator);
                Object example = OpenApiDocUtils.getExampleValue(field, operator);

                Parameter parameter = new Parameter()
                        .name(paramName)
                        .in("query")
                        .description(getOperatorDescription(fieldDescription, operator, example))
                        .schema(schema)
                        .required(false);

                operation.getParameters().add(parameter);
            }
        }
    }

    private void addPaginationParameters(Operation operation) {
        operation.getParameters().add(new Parameter()
                .name("page")
                .in("query")
                .description("Page number (0-based)")
                .schema(new Schema<Integer>().type("integer").minimum(new BigDecimal(0)))
                .example(0)
                .required(false));

        operation.getParameters().add(new Parameter()
                .name("size")
                .in("query")
                .description("Items per page")
                .schema(new Schema<Integer>().type("integer").minimum(new BigDecimal(1)))
                .example(20)
                .required(false));
    }

    @SuppressWarnings("unchecked")
    private void addSortingParameters(Operation operation, Class<?> dtoClass) {
        List<String> sortableFields = Arrays.stream(dtoClass.getDeclaredFields())
                .filter(field -> {
                    SearchableField annotation = field.getAnnotation(SearchableField.class);
                    return annotation != null && annotation.sortable();
                })
                .map(Field::getName)
                .collect(Collectors.toList());

        if (!sortableFields.isEmpty()) {
            operation.getParameters().add(new Parameter()
                    .name("sort")
                    .in("query")
                    .description("Sort fields (e.g., field.asc or field.desc). Available fields: " +
                            String.join(", ", sortableFields))
                    .schema(new Schema<List<String>>()
                            .type("array")
                            .items(new Schema<String>().type("string")))
                    .explode(true)
                    .required(false));
        }
    }

    private Schema<?> createFieldSchema(Field field, SearchOperator op) {
        Schema<?> schema = new Schema<>();
        Class<?> fieldType = field.getType();

        if (op == SearchOperator.IS_NULL || op == SearchOperator.IS_NOT_NULL) {
            schema.type("boolean");
            return schema;
        }

        if (op == SearchOperator.IN || op == SearchOperator.NOT_IN) {
            schema.type("string");
            schema.description("Enter multiple values separated by comma");
            return schema;
        }

        if (op == SearchOperator.BETWEEN) {
            schema.type("string");
            schema.description("Enter two values separated by comma");
            return schema;
        }

        setFieldTypeSchema(schema, fieldType);
        return schema;
    }

    private void setFieldTypeSchema(Schema<?> schema, Class<?> fieldType) {
        if (fieldType == LocalDateTime.class) {
            schema.type("string").format("date-time");
            schema.description("Format: " + dateFormatter);
        } else if (fieldType.isEnum()) {
            schema.type("string");
            @SuppressWarnings({"unchecked"})
            Schema<Object> objSchema = (Schema<Object>) schema;
            objSchema.setEnum(Arrays.asList(fieldType.getEnumConstants()));
        } else if (Number.class.isAssignableFrom(fieldType) || fieldType.isPrimitive()) {
            if (fieldType == Long.class || fieldType == long.class) {
                schema.type("integer").format("int64");
            } else if (fieldType == Integer.class || fieldType == int.class) {
                schema.type("integer").format("int32");
            } else if (fieldType == Double.class || fieldType == double.class ||
                    fieldType == Float.class || fieldType == float.class) {
                schema.type("number");
                schema.format(fieldType == Double.class || fieldType == double.class ? "double" : "float");
            }
        } else {
            schema.type("string");
        }
    }

    private String getFieldDescription(Field field) {
        io.swagger.v3.oas.annotations.media.Schema schema = field
                .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return schema != null && !schema.description().isEmpty() ? schema.description() : field.getName();
    }

    private String getOperatorDescription(String fieldDescription, SearchOperator operator, Object example) {
        String desc = String.format("%s %s", fieldDescription, OpenApiDocUtils.getOperationDescription(operator));
        if (example != null) {
            String formattedExample = formatExample(example, operator);
            desc += String.format(" (e.g., %s)", formattedExample);
        }
        return desc;
    }

    private String formatExample(Object example, SearchOperator operator) {
        if (example == null) return null;
        if (example instanceof LocalDateTime) {
            return ((LocalDateTime) example).format(dateFormatter);
        }
        if (example instanceof List) {
            List<?> values = (List<?>) example;
            if (operator == SearchOperator.BETWEEN && values.size() >= 2) {
                return values.stream()
                        .limit(2)
                        .map(v -> v instanceof LocalDateTime ?
                                ((LocalDateTime) v).format(dateFormatter) :
                                String.valueOf(v))
                        .collect(Collectors.joining(","));
            }
            if (operator == SearchOperator.IN || operator == SearchOperator.NOT_IN) {
                return values.stream()
                        .limit(2)
                        .map(String::valueOf)
                        .collect(Collectors.joining(","));
            }
        }
        return String.valueOf(example);
    }
} 