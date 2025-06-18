package dev.simplecore.searchable.core.service.cursor;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Calculates cursor values for cursor-based pagination.
 * Extracts field values from entities at specific positions to create cursor conditions.
 *
 * @param <T> The entity type
 */
@Slf4j
public class CursorCalculator<T> {

    private final JpaSpecificationExecutor<T> specificationExecutor;
    private final Class<T> entityClass;

    public CursorCalculator(@NonNull JpaSpecificationExecutor<T> specificationExecutor,
                           @NonNull Class<T> entityClass) {
        this.specificationExecutor = specificationExecutor;
        this.entityClass = entityClass;
    }

    /**
     * Calculates cursor values for the specified offset position.
     * 
     * @param targetOffset the target offset position (0-based)
     * @param baseSpec the base specification for filtering
     * @param sort the sort criteria
     * @return map of field names to cursor values
     */
    public Map<String, Object> calculateCursorValues(int targetOffset,
                                                   Specification<T> baseSpec,
                                                   @NonNull Sort sort) {
        if (targetOffset < 0) {
            return Collections.emptyMap();
        }

        try {
            // Create PageRequest to fetch the record at target offset
            PageRequest singleRecordRequest = PageRequest.of(targetOffset, 1, sort);
            
            // Execute query to get the target record
            org.springframework.data.domain.Page<T> page = specificationExecutor.findAll(baseSpec, singleRecordRequest);
            
            if (page.hasContent()) {
                T targetEntity = page.getContent().get(0);
                return extractCursorValues(targetEntity, sort);
            }
            
            return Collections.emptyMap();
            
        } catch (Exception e) {
            log.warn("Failed to calculate cursor values at offset {}: {}", targetOffset, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Extracts cursor values from an entity based on sort criteria.
     * 
     * @param entity the entity to extract values from
     * @param sort the sort criteria
     * @return map of field names to cursor values
     */
    public Map<String, Object> extractCursorValues(@NonNull T entity, @NonNull Sort sort) {
        Map<String, Object> cursorValues = new LinkedHashMap<>();
        
        for (Sort.Order order : sort) {
            String fieldName = order.getProperty();
            try {
                Object fieldValue = getFieldValue(entity, fieldName);
                cursorValues.put(fieldName, fieldValue);
            } catch (Exception e) {
                log.warn("Failed to extract cursor value for field '{}': {}", fieldName, e.getMessage());
                // Continue with other fields even if one fails
            }
        }
        
        return cursorValues;
    }

    /**
     * Gets field value from entity, supporting nested field access.
     * 
     * @param entity the entity object
     * @param fieldPath the field path (supports dot notation like "author.name")
     * @return the field value
     */
    private Object getFieldValue(Object entity, String fieldPath) {
        if (entity == null || fieldPath == null || fieldPath.trim().isEmpty()) {
            return null;
        }

        String[] pathParts = fieldPath.split("\\.");
        Object currentObject = entity;

        for (String part : pathParts) {
            if (currentObject == null) {
                return null;
            }
            currentObject = getDirectFieldValue(currentObject, part);
        }

        return convertFieldValue(currentObject);
    }

    /**
     * Gets direct field value from object using reflection.
     * 
     * @param object the object to get value from
     * @param fieldName the field name
     * @return the field value
     */
    private Object getDirectFieldValue(Object object, String fieldName) {
        if (object == null || fieldName == null) {
            return null;
        }

        try {
            // Try getter method first
            String getterName = "get" + capitalize(fieldName);
            Method getter = findMethod(object.getClass(), getterName);
            if (getter != null) {
                getter.setAccessible(true);
                return getter.invoke(object);
            }

            // Try boolean getter
            String booleanGetterName = "is" + capitalize(fieldName);
            Method booleanGetter = findMethod(object.getClass(), booleanGetterName);
            if (booleanGetter != null) {
                booleanGetter.setAccessible(true);
                return booleanGetter.invoke(object);
            }

            // Try direct field access
            Field field = findField(object.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(object);
            }

            log.warn("Field '{}' not found in class {}", fieldName, object.getClass().getSimpleName());
            return null;

        } catch (Exception e) {
            log.warn("Failed to get field value '{}' from object: {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Finds method in class hierarchy.
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Finds field in class hierarchy.
     */
    private Field findField(Class<?> clazz, String fieldName) {
        Class<?> currentClass = clazz;
        while (currentClass != null) {
            try {
                return currentClass.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Capitalizes first letter of string.
     */
    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    /**
     * Converts field value to appropriate type for cursor comparison.
     * Handles special types like dates, enums, etc.
     * 
     * @param value the original field value
     * @return converted value suitable for cursor operations
     */
    private Object convertFieldValue(Object value) {
        if (value == null) {
            return null;
        }

        // Handle temporal types
        if (value instanceof LocalDateTime || value instanceof LocalDate || 
            value instanceof Date || value instanceof java.sql.Timestamp) {
            return value; // Keep as-is for temporal comparisons
        }

        // Handle enums
        if (value instanceof Enum) {
            return value; // Keep as-is for enum comparisons
        }

        // Handle strings
        if (value instanceof String) {
            return value;
        }

        // Handle numbers
        if (value instanceof Number) {
            return value;
        }

        // Handle booleans
        if (value instanceof Boolean) {
            return value;
        }

        // For other types, convert to string as fallback
        return value.toString();
    }
} 