package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ParameterGenerator {
    private final ParameterSchemaGenerator schemaGenerator;

    public ParameterGenerator() {
        this.schemaGenerator = new ParameterSchemaGenerator();
    }

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

                Schema<?> schema = schemaGenerator.createFieldParameterSchema(field, operator);

                Parameter parameter = new Parameter()
                        .name(paramName)
                        .in("query")
                        .description(getOperatorDescription(fieldDescription, operator))
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
                .schema(schemaGenerator.createPageSchema())
                .example(0)
                .required(false));

        operation.getParameters().add(new Parameter()
                .name("size")
                .in("query")
                .description("Items per page")
                .schema(schemaGenerator.createSizeSchema())
                .example(20)
                .required(false));
    }

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
                    .schema(schemaGenerator.createSortSchema())
                    .explode(true)
                    .required(false));
        }
    }

    private String getFieldDescription(Field field) {
        io.swagger.v3.oas.annotations.media.Schema schema = field
                .getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);
        return schema != null && !schema.description().isEmpty() ? schema.description() : field.getName();
    }

    private String getOperatorDescription(String fieldDescription, SearchOperator operator) {
        return String.format("%s - %s", fieldDescription, OpenApiDocUtils.getOperationDescription(operator));
    }
} 