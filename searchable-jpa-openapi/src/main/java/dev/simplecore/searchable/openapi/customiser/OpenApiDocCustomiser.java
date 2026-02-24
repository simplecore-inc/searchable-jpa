package dev.simplecore.searchable.openapi.customiser;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import dev.simplecore.searchable.openapi.generator.DescriptionGenerator;
import dev.simplecore.searchable.openapi.generator.ExampleGenerator;
import dev.simplecore.searchable.openapi.generator.ParameterGenerator;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.MethodParameter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenApiDocCustomiser implements OperationCustomizer {
    private static final Logger log = LoggerFactory.getLogger(OpenApiDocCustomiser.class);
    private static final String MEDIA_TYPE_JSON = "application/json";

    private final ExampleGenerator exampleGenerator;
    private final ParameterGenerator parameterGenerator;

    public OpenApiDocCustomiser(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.exampleGenerator = new ExampleGenerator();
        this.parameterGenerator = new ParameterGenerator();
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        log.trace("Customizing OpenAPI operation for search conditions");

        try {
            Arrays.stream(handlerMethod.getMethodParameters())
                    .filter(this::isSearchConditionParameter)
                    .findFirst()
                    .ifPresent(param -> customizeForParam(param, operation));
        } catch (Exception e) {
            log.error("Error customizing OpenAPI operation for method: {}", handlerMethod.getMethod().getName(), e);
        }

        return operation;
    }

    private boolean isSearchConditionParameter(MethodParameter param) {
        Class<?> parameterType = param.getParameterType();
        return SearchCondition.class.isAssignableFrom(parameterType) ||
                param.hasParameterAnnotation(SearchableParams.class);
    }

    private void customizeForParam(MethodParameter param, Operation operation) {
        Class<?> dtoClass = extractDtoClass(param);
        if (dtoClass == null) return;

        boolean isPostType = SearchCondition.class.isAssignableFrom(param.getParameterType());

        DescriptionGenerator.customizeOperation(operation, dtoClass);

        if (isPostType) {
            customizeRequestBody(operation, dtoClass);
        } else {
            parameterGenerator.customizeParameters(operation, dtoClass);
        }
    }

    private Class<?> extractDtoClass(MethodParameter param) {
        if (param.hasParameterAnnotation(SearchableParams.class)) {
            SearchableParams annotation = param.getParameterAnnotation(SearchableParams.class);
            return annotation != null ? annotation.value() : null;
        }
        return (Class<?>) ((ParameterizedType) param.getGenericParameterType()).getActualTypeArguments()[0];
    }

    private void customizeRequestBody(Operation operation, Class<?> dtoClass) {
        try {
            RequestBody requestBody = new RequestBody();
            Content content = new Content();
            MediaType mediaType = new MediaType();

            registerEnumsFromDto(dtoClass);

            java.lang.reflect.Type parameterizedType = new ParameterizedType() {
                @Override
                public java.lang.reflect.Type[] getActualTypeArguments() {
                    return new java.lang.reflect.Type[]{dtoClass};
                }

                @Override
                public java.lang.reflect.Type getRawType() {
                    return SearchCondition.class;
                }

                @Override
                public java.lang.reflect.Type getOwnerType() {
                    return null;
                }
            };

            Schema<?> schema = ModelConverters.getInstance().resolveAsResolvedSchema(
                    new AnnotatedType(parameterizedType)
                            .resolveAsRef(false)
                            .skipSchemaName(true)
            ).schema;

            Map<String, Example> examples = new LinkedHashMap<>();
            Example example = new Example();
            example.setValue(exampleGenerator.generateSimpleExample(dtoClass));
            example.setSummary("Example");
            examples.put("example", example);

            mediaType.schema(schema);
            mediaType.examples(examples);
            content.addMediaType(MEDIA_TYPE_JSON, mediaType);
            requestBody.content(content);
            requestBody.required(true);
            operation.requestBody(requestBody);
        } catch (Exception e) {
            log.error("Failed to customize request body", e);
        }
    }

    private void registerEnumsFromDto(Class<?> dtoClass) {
        ModelConverters modelConverters = ModelConverters.getInstance();

        for (Field field : dtoClass.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            if (fieldType.isEnum()) {
                try {
                    modelConverters.resolveAsResolvedSchema(
                        new AnnotatedType(fieldType).resolveAsRef(true)
                    );
                    log.trace("Registered enum schema: {}", fieldType.getSimpleName());
                } catch (Exception e) {
                    log.warn("Failed to register enum schema: {}", fieldType.getSimpleName(), e);
                }
            }
        }
    }
}
