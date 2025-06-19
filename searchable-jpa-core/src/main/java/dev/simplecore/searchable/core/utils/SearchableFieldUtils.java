package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.annotation.SearchableField;
import javax.persistence.Id;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SearchableFieldUtils {
    private static final Logger log = LoggerFactory.getLogger(SearchableFieldUtils.class);

    public static String getEntityFieldFromDto(Class<?> dtoClass, String field) {
        if (dtoClass == null) {
            return field;
        }

        try {
            java.lang.reflect.Field dtoField = dtoClass.getDeclaredField(field);
            SearchableField annotation = dtoField.getAnnotation(SearchableField.class);
            return annotation != null && !annotation.entityField().isEmpty() ? annotation.entityField() : field;
        } catch (NoSuchFieldException e) {
            return field;
        }
    }

    /**
     * Gets the primary key field name from the entity class.
     * This is used for cursor-based pagination to ensure unique ordering.
     * 
     * @param entityManager the entity manager
     * @param entityClass the entity class
     * @return the primary key field name, or null if not found
     */
    public static String getPrimaryKeyFieldName(EntityManager entityManager, Class<?> entityClass) {
        try {
            EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);
            
            // Try to get the ID attribute from metamodel
            if (entityType.hasSingleIdAttribute()) {
                SingularAttribute<?, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
                return idAttribute.getName();
            }
            
            // Fallback: search for @Id annotation using reflection
            return findIdFieldByReflection(entityClass);
            
        } catch (Exception e) {
            log.warn("Failed to get primary key field name for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
            return findIdFieldByReflection(entityClass);
        }
    }
    
    /**
     * Finds the ID field using reflection as a fallback method.
     * 
     * @param entityClass the entity class
     * @return the ID field name, or null if not found
     */
    private static String findIdFieldByReflection(Class<?> entityClass) {
        try {
            // Search in the class hierarchy
            Class<?> currentClass = entityClass;
            while (currentClass != null && !currentClass.equals(Object.class)) {
                // Check fields
                for (Field field : currentClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        return field.getName();
                    }
                }
                
                // Check methods (getters)
                for (Method method : currentClass.getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Id.class) && method.getName().startsWith("get")) {
                        String fieldName = method.getName().substring(3);
                        return Character.toLowerCase(fieldName.charAt(0)) + fieldName.substring(1);
                    }
                }
                
                currentClass = currentClass.getSuperclass();
            }
            
            // Common fallback field names
            String[] commonIdFields = {"id", "uuid", "identifier"};
            for (String fieldName : commonIdFields) {
                if (hasField(entityClass, fieldName)) {
                    log.info("Using fallback ID field '{}' for entity {}", fieldName, entityClass.getSimpleName());
                    return fieldName;
                }
            }
            
            log.warn("No ID field found for entity {}", entityClass.getSimpleName());
            return null;
            
        } catch (Exception e) {
            log.warn("Failed to find ID field by reflection for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
            return null;
        }
    }
    
    /**
     * Checks if the entity class has a field with the given name.
     * 
     * @param entityClass the entity class
     * @param fieldName the field name to check
     * @return true if the field exists, false otherwise
     */
    private static boolean hasField(Class<?> entityClass, String fieldName) {
        try {
            Class<?> currentClass = entityClass;
            while (currentClass != null && !currentClass.equals(Object.class)) {
                try {
                    currentClass.getDeclaredField(fieldName);
                    return true;
                } catch (NoSuchFieldException e) {
                    // Continue searching in superclass
                }
                currentClass = currentClass.getSuperclass();
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
} 