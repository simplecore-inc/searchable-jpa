package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.ResolvedSchema;
import io.swagger.v3.oas.models.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generator for parameter schemas that creates reusable schema definitions
 * in components.schemas and returns references to them.
 */
public class ParameterSchemaGenerator {
    private static final Logger log = LoggerFactory.getLogger(ParameterSchemaGenerator.class);
    private static final String DEFAULT_DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);

    private final ModelConverters modelConverters;
    private final Map<String, Schema<?>> registeredSchemas;

    public ParameterSchemaGenerator() {
        this.modelConverters = ModelConverters.getInstance();
        this.registeredSchemas = new HashMap<>();
    }

    /**
     * Creates or gets a reference to a schema for a field parameter.
     * For enums and complex types, returns a $ref to components.schemas.
     * For simple types, returns inline schema.
     */
    public Schema<?> createFieldParameterSchema(Field field, SearchOperator operator) {
        Class<?> fieldType = field.getType();
        Type genericType = field.getGenericType();

        // Special handling for different operators
        if (operator == SearchOperator.IS_NULL || operator == SearchOperator.IS_NOT_NULL) {
            return createBooleanSchema();
        }

        if (operator == SearchOperator.IN || operator == SearchOperator.NOT_IN) {
            return createCommaSeparatedSchema("Enter multiple values separated by comma");
        }

        if (operator == SearchOperator.BETWEEN) {
            return createCommaSeparatedSchema("Enter two values separated by comma");
        }

        // For enum types, register as component and return reference
        if (fieldType.isEnum()) {
            return getOrCreateEnumSchema(fieldType);
        }

        // Check if String field has enum constraints (e.g., through @Schema annotation)
        if (fieldType == String.class) {
            Schema<?> stringWithEnumSchema = createStringSchemaWithEnumConstraints(field);
            if (stringWithEnumSchema != null) {
                return stringWithEnumSchema;
            }
        }

        // For complex types that should be registered as components
        if (shouldRegisterAsComponent(fieldType)) {
            return getOrCreateComponentSchema(fieldType);
        }

        // For simple types and collections, return inline schema with generic type info
        return createInlineSchema(fieldType, genericType);
    }

    /**
     * Gets or creates a schema reference for an enum type.
     * Registers the enum as a component schema and returns $ref.
     */
    private Schema<?> getOrCreateEnumSchema(Class<?> enumType) {
        String schemaName = enumType.getSimpleName();

        // Check if already registered
        if (registeredSchemas.containsKey(schemaName)) {
            return createRefSchema(schemaName);
        }

        try {
            // Register enum as a component schema with enum values
            ResolvedSchema resolvedSchema = modelConverters.resolveAsResolvedSchema(
                new AnnotatedType(enumType).resolveAsRef(true)
            );

            if (resolvedSchema != null && resolvedSchema.schema != null) {
                registeredSchemas.put(schemaName, resolvedSchema.schema);
                log.debug("Registered enum schema as component: {}", schemaName);

                // Return reference to the registered schema
                return createRefSchema(schemaName);
            }
        } catch (Exception e) {
            log.warn("Failed to register enum schema: {}", schemaName, e);
        }

        // Fallback: create and register manually if ModelConverters fails
        Schema<String> enumSchema = new Schema<>();
        enumSchema.type("string");
        enumSchema.name(schemaName);

        // Extract enum constant names
        List<String> enumValues = Arrays.stream(enumType.getEnumConstants())
                .map(e -> ((Enum<?>) e).name())
                .collect(Collectors.toList());
        enumSchema.setEnum(enumValues);
        enumSchema.description(enumType.getSimpleName() + " enum");

        // Register in our cache
        registeredSchemas.put(schemaName, enumSchema);

        // Try to register with ModelConverters
        try {
            modelConverters.resolveAsResolvedSchema(
                new AnnotatedType(enumType).resolveAsRef(true).name(schemaName)
            );
        } catch (Exception e) {
            log.debug("Could not register with ModelConverters: {}", e.getMessage());
        }

        // Return reference
        return createRefSchema(schemaName);
    }

    /**
     * Gets or creates a schema reference for a complex type.
     */
    private Schema<?> getOrCreateComponentSchema(Class<?> type) {
        String schemaName = type.getSimpleName();

        // Check if already registered
        if (registeredSchemas.containsKey(schemaName)) {
            return createRefSchema(schemaName);
        }

        try {
            // Register type as a component schema
            ResolvedSchema resolvedSchema = modelConverters.resolveAsResolvedSchema(
                new AnnotatedType(type).resolveAsRef(true)
            );

            if (resolvedSchema != null && resolvedSchema.schema != null) {
                registeredSchemas.put(schemaName, resolvedSchema.schema);
                log.debug("Registered component schema: {}", schemaName);
                return resolvedSchema.schema;
            }
        } catch (Exception e) {
            log.warn("Failed to register component schema: {}", schemaName, e);
        }

        // Fallback to inline schema if registration fails
        return createInlineSchema(type);
    }

    /**
     * Creates a reference schema pointing to components.schemas.
     */
    private Schema<?> createRefSchema(String schemaName) {
        Schema<?> refSchema = new Schema<>();
        refSchema.set$ref("#/components/schemas/" + schemaName);
        return refSchema;
    }

    /**
     * Creates an inline schema for simple types.
     */
    private Schema<?> createInlineSchema(Class<?> fieldType) {
        return createInlineSchema(fieldType, null);
    }

    /**
     * Creates an inline schema for simple types with generic type information.
     */
    private Schema<?> createInlineSchema(Class<?> fieldType, Type genericType) {
        Schema<?> schema = new Schema<>();

        // Handle Map types
        if (java.util.Map.class.isAssignableFrom(fieldType)) {
            schema.type("object");

            // Try to extract value type from generic type
            Schema<?> valueSchema = new Schema<>();
            if (genericType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (typeArgs.length >= 2) {
                    // Map<K, V> - use V type
                    valueSchema = createSchemaForType(typeArgs[1]);
                } else {
                    valueSchema.type("string");
                }
            } else {
                valueSchema.type("string");
            }
            schema.additionalProperties(valueSchema);
            return schema;
        }

        // Handle List/Set/Collection types
        if (java.util.List.class.isAssignableFrom(fieldType) ||
            java.util.Set.class.isAssignableFrom(fieldType) ||
            java.util.Collection.class.isAssignableFrom(fieldType)) {
            schema.type("array");

            // Try to extract element type from generic type
            Schema<?> itemSchema = new Schema<>();
            if (genericType instanceof ParameterizedType) {
                Type[] typeArgs = ((ParameterizedType) genericType).getActualTypeArguments();
                if (typeArgs.length >= 1) {
                    // List<T> - use T type
                    itemSchema = createSchemaForType(typeArgs[0]);
                } else {
                    itemSchema.type("string");
                }
            } else {
                itemSchema.type("string");
            }
            schema.items(itemSchema);
            return schema;
        }

        // Handle basic types
        return createSchemaForType(fieldType);
    }

    /**
     * Creates a schema for a given type (used for generic type arguments).
     */
    private Schema<?> createSchemaForType(Type type) {
        Schema<?> schema = new Schema<>();

        // Extract raw type if it's a ParameterizedType
        Class<?> rawType;
        if (type instanceof ParameterizedType) {
            rawType = (Class<?>) ((ParameterizedType) type).getRawType();
        } else if (type instanceof Class) {
            rawType = (Class<?>) type;
        } else {
            // Fallback to string for unknown types
            schema.type("string");
            return schema;
        }

        // Handle date/time types
        if (rawType == LocalDateTime.class) {
            schema.type("string").format("date-time");
            schema.description("Format: " + DEFAULT_DATE_FORMAT);
        } else if (rawType == Long.class || rawType == long.class) {
            schema.type("integer").format("int64");
        } else if (rawType == Integer.class || rawType == int.class) {
            schema.type("integer").format("int32");
        } else if (rawType == Double.class || rawType == double.class) {
            schema.type("number").format("double");
        } else if (rawType == Float.class || rawType == float.class) {
            schema.type("number").format("float");
        } else if (rawType == Boolean.class || rawType == boolean.class) {
            schema.type("boolean");
        } else {
            schema.type("string");
        }

        return schema;
    }

    /**
     * Creates a boolean schema.
     */
    private Schema<?> createBooleanSchema() {
        return new Schema<>().type("boolean");
    }

    /**
     * Creates a comma-separated string schema.
     */
    private Schema<?> createCommaSeparatedSchema(String description) {
        Schema<?> schema = new Schema<>();
        schema.type("string");
        schema.description(description);
        return schema;
    }

    /**
     * Determines if a type should be registered as a component schema.
     */
    private boolean shouldRegisterAsComponent(Class<?> type) {
        // Don't register Java collections and maps as components
        if (java.util.Map.class.isAssignableFrom(type) ||
            java.util.Collection.class.isAssignableFrom(type) ||
            java.util.List.class.isAssignableFrom(type) ||
            java.util.Set.class.isAssignableFrom(type)) {
            return false;
        }

        // Register enums and custom domain types as components
        return type.isEnum() ||
               (!type.isPrimitive() &&
                !type.getPackage().getName().startsWith("java.") &&
                !type.getPackage().getName().startsWith("javax.") &&
                !type.getPackage().getName().startsWith("jakarta."));
    }

    /**
     * Creates pagination parameter schemas.
     */
    public Schema<?> createPageSchema() {
        return new Schema<Integer>()
                .type("integer")
                .format("int32")
                .minimum(new BigDecimal(0))
                .description("Page number (0-based)");
    }

    public Schema<?> createSizeSchema() {
        return new Schema<Integer>()
                .type("integer")
                .format("int32")
                .minimum(new BigDecimal(1))
                .description("Items per page");
    }

    /**
     * Creates sort parameter schema.
     */
    @SuppressWarnings("unchecked")
    public Schema<?> createSortSchema() {
        return new Schema<>()
                .type("array")
                .items(new Schema<String>().type("string"));
    }

    /**
     * Creates a String schema with enum constraints if applicable.
     * Checks for @Schema annotation with allowableValues or implementation.
     * Also checks for @Pattern annotations that might indicate enum-like constraints.
     */
    private Schema<?> createStringSchemaWithEnumConstraints(Field field) {
        // Check for @Schema annotation
        io.swagger.v3.oas.annotations.media.Schema schemaAnnotation =
            field.getAnnotation(io.swagger.v3.oas.annotations.media.Schema.class);

        if (schemaAnnotation != null) {
            // Check for allowableValues
            String[] allowableValues = schemaAnnotation.allowableValues();
            if (allowableValues != null && allowableValues.length > 0) {
                Schema<String> stringSchema = new Schema<>();
                stringSchema.type("string");
                stringSchema.setEnum(Arrays.asList(allowableValues));
                if (!schemaAnnotation.description().isEmpty()) {
                    stringSchema.description(schemaAnnotation.description());
                }
                return stringSchema;
            }

            // Check for implementation (enum reference)
            Class<?> implementation = schemaAnnotation.implementation();
            if (!implementation.equals(Void.class) && implementation.isEnum()) {
                // Create string schema with enum values
                Schema<String> stringSchema = new Schema<>();
                stringSchema.type("string");
                List<String> enumValues = Arrays.stream(implementation.getEnumConstants())
                        .map(e -> ((Enum<?>) e).name())
                        .collect(Collectors.toList());
                stringSchema.setEnum(enumValues);
                if (!schemaAnnotation.description().isEmpty()) {
                    stringSchema.description(schemaAnnotation.description());
                }
                return stringSchema;
            }
        }

        // Check for @Pattern annotation that might indicate enum-like constraints
        jakarta.validation.constraints.Pattern pattern =
            field.getAnnotation(jakarta.validation.constraints.Pattern.class);
        if (pattern != null) {
            String regexp = pattern.regexp();
            // Check for common enum-like patterns (e.g., "ACTIVE|INACTIVE|PENDING")
            if (regexp.contains("|") && !regexp.contains("[") && !regexp.contains("*")) {
                String[] values = regexp.replace("^", "").replace("$", "").replace("(", "").replace(")", "").split("\\|");
                if (values.length > 1 && values.length <= 20) { // Reasonable limit for enum-like values
                    Schema<String> stringSchema = new Schema<>();
                    stringSchema.type("string");
                    stringSchema.setEnum(Arrays.asList(values));
                    stringSchema.description("Pattern: " + regexp);
                    return stringSchema;
                }
            }
        }

        return null; // No enum constraints found
    }
}