package dev.simplecore.searchable.openapi.customiser;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.openapi.annotation.SearchableParams;
import dev.simplecore.searchable.openapi.generator.DescriptionGenerator;
import dev.simplecore.searchable.openapi.generator.ExampleGenerator;
import dev.simplecore.searchable.openapi.generator.ParameterGenerator;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
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
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class OpenApiDocCustomiser implements OperationCustomizer {
    private static final Logger log = LoggerFactory.getLogger(OpenApiDocCustomiser.class);
    private static final String MEDIA_TYPE_JSON = "application/json";

    private final RequestMappingHandlerMapping handlerMapping;
    private final ExampleGenerator exampleGenerator;
    private final ParameterGenerator parameterGenerator;

    public OpenApiDocCustomiser(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping) {
        this.handlerMapping = handlerMapping;
        this.exampleGenerator = new ExampleGenerator();
        this.parameterGenerator = new ParameterGenerator();
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        log.trace("Customizing OpenAPI operation for search conditions (Spring Boot 3.x)");

        try {
            Arrays.stream(handlerMethod.getMethodParameters())
                    .filter(this::isSearchConditionParameter)
                    .findFirst()
                    .ifPresent(param -> customizeOperationForMethod(param, operation, handlerMethod));
        } catch (Exception e) {
            log.error("Error customizing OpenAPI operation for method: {}", handlerMethod.getMethod().getName(), e);
        }

        return operation;
    }

    private void initializeOpenApiPaths(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            log.trace("OpenAPI paths is null, initializing new Paths");
            openApi.setPaths(new io.swagger.v3.oas.models.Paths());
        }
    }

    private void customizeHandlerMethods(OpenAPI openApi) {
        handlerMapping.getHandlerMethods().forEach((requestMapping, handlerMethod) -> {
            try {
                customizeSearchCondition(openApi, requestMapping, handlerMethod);
            } catch (Exception e) {
                log.error("Error customizing search condition for handler method: {}", handlerMethod, e);
            }
        });
    }

    private void customizeSearchCondition(OpenAPI openApi, RequestMappingInfo requestMapping,
                                          HandlerMethod handlerMethod) {
        Set<String> patterns = requestMapping.getPatternValues();
        if (patterns.isEmpty()) return;

        patterns.forEach(pattern -> {
            PathItem pathItem = openApi.getPaths().get(pattern);
            if (pathItem == null) return;

            Operation operation = getOperation(pathItem, requestMapping);
            if (operation == null) return;

            processSearchConditionParameter(handlerMethod, operation, pattern);
        });
    }

    private void processSearchConditionParameter(HandlerMethod handlerMethod, Operation operation, String pattern) {
        Arrays.stream(handlerMethod.getMethodParameters())
                .filter(this::isSearchConditionParameter)
                .findFirst()
                .ifPresent(param -> customizeOperation(param, operation, pattern));
    }

    private boolean isSearchConditionParameter(MethodParameter param) {
        Class<?> parameterType = param.getParameterType();
        return SearchCondition.class.isAssignableFrom(parameterType) ||
                param.hasParameterAnnotation(SearchableParams.class);
    }

    private void customizeOperationForMethod(MethodParameter param, Operation operation, HandlerMethod handlerMethod) {
        Class<?> dtoClass = extractDtoClass(param);
        if (dtoClass == null) return;

        boolean isPostType = isPostTypeParameter(param);

        // Extract path pattern from handler method
        String pathPattern = extractPathPattern(handlerMethod);

        DescriptionGenerator.customizeOperation(
                operation,
                dtoClass,
                pathPattern,
                isPostType ? DescriptionGenerator.RequestType.POST : DescriptionGenerator.RequestType.GET
        );

        if (isPostType) {
            customizeRequestBody(operation, dtoClass);
        } else {
            parameterGenerator.customizeParameters(operation, dtoClass);
        }
    }

    private void customizeOperation(MethodParameter param, Operation operation, String pattern) {
        Class<?> dtoClass = extractDtoClass(param);
        if (dtoClass == null) return;

        boolean isPostType = isPostTypeParameter(param);

        DescriptionGenerator.customizeOperation(
                operation,
                dtoClass,
                pattern,
                isPostType ? DescriptionGenerator.RequestType.POST : DescriptionGenerator.RequestType.GET
        );

        if (isPostType) {
            customizeRequestBody(operation, dtoClass);
        } else {
            parameterGenerator.customizeParameters(operation, dtoClass);
        }
    }

    private boolean isPostTypeParameter(MethodParameter param) {
        return SearchCondition.class.isAssignableFrom(param.getParameterType());
    }

    private Class<?> extractDtoClass(MethodParameter param) {
        if (param.hasParameterAnnotation(SearchableParams.class)) {
            SearchableParams annotation = param.getParameterAnnotation(SearchableParams.class);
            return annotation != null ? annotation.value() : null;
        }
        return (Class<?>) ((ParameterizedType) param.getGenericParameterType()).getActualTypeArguments()[0];
    }

    private String extractPathPattern(HandlerMethod handlerMethod) {
        try {
            // Extract path pattern through handler method mapping information
            for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
                if (entry.getValue().equals(handlerMethod)) {
                    Set<String> patterns = entry.getKey().getPatternValues();
                    if (!patterns.isEmpty()) {
                        return patterns.iterator().next();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract path pattern from handler method: {}", handlerMethod.getMethod().getName(), e);
        }

        // Use method name as default value
        return "/" + handlerMethod.getMethod().getName();
    }

    private Operation getOperation(PathItem pathItem, RequestMappingInfo requestMapping) {
        Set<RequestMethod> methods = requestMapping.getMethodsCondition().getMethods();
        if (methods.contains(RequestMethod.GET))
            return pathItem.getGet();
        if (methods.contains(RequestMethod.POST))
            return pathItem.getPost();
        if (methods.contains(RequestMethod.PUT))
            return pathItem.getPut();
        if (methods.contains(RequestMethod.DELETE))
            return pathItem.getDelete();
        return null;
    }

    private void customizeRequestBody(Operation operation, Class<?> dtoClass) {
        try {
            RequestBody requestBody = new RequestBody();
            Content content = new Content();
            MediaType mediaType = new MediaType();

            // Register enum types from DTO for $ref usage
            registerEnumsFromDto(dtoClass);

            // Create parameterized type for SearchCondition<DTO>
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

            // Generate schema inline (not using $ref for complex generic types)
            Schema<?> schema = ModelConverters.getInstance().resolveAsResolvedSchema(
                    new AnnotatedType(parameterizedType)
                            .resolveAsRef(false)
                            .skipSchemaName(true)
            ).schema;

            Map<String, Example> examples = new LinkedHashMap<>();

            String simpleExample = exampleGenerator.generateSimpleExample(dtoClass);
            Example simple = new Example();
            simple.setValue(simpleExample);
            simple.setDescription("Simple example with one filter");
            simple.setSummary("Simple Example");
            examples.put("simple", simple);

            Example complete = new Example();
            complete.setValue(exampleGenerator.generateCompleteExample(dtoClass));
            complete.setDescription("Complete example with multiple filters");
            complete.setSummary("Complete Example");
            examples.put("complete", complete);

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

    /**
     * Registers enum types found in DTO fields as component schemas for $ref usage
     */
    private void registerEnumsFromDto(Class<?> dtoClass) {
        ModelConverters modelConverters = ModelConverters.getInstance();

        for (Field field : dtoClass.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            if (fieldType.isEnum()) {
                try {
                    // Register enum as component schema
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