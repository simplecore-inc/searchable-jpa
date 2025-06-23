package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.annotation.SearchableField;
import javax.persistence.Id;
import javax.persistence.EmbeddedId;
import javax.persistence.metamodel.EntityType;
import javax.persistence.metamodel.SingularAttribute;
import javax.persistence.EntityManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
            
            // Check if it's a single ID attribute (@EmbeddedId or simple @Id)
            if (entityType.hasSingleIdAttribute()) {
                SingularAttribute<?, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
                
                // Check if this is an @EmbeddedId (composite key embedded in a single object)
                if (isEmbeddedId(entityClass, idAttribute.getName())) {
                    log.debug("@EmbeddedId composite key detected for entity {}: using special handling", entityClass.getSimpleName());
                    return handleCompositeKey(entityType, entityClass);
                } else {
                    // Simple @Id
                    return idAttribute.getName();
                }
            } else {
                // Handle composite key with @IdClass
                log.debug("@IdClass composite key detected for entity {}: using special handling", entityClass.getSimpleName());
                return handleCompositeKey(entityType, entityClass);
            }
            
        } catch (Exception e) {
            log.warn("Failed to get primary key field name for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
            return findIdFieldByReflection(entityClass);
        }
    }
    
    /**
     * Checks if the given field is an @EmbeddedId field.
     * 
     * @param entityClass the entity class
     * @param fieldName the field name to check
     * @return true if the field is annotated with @EmbeddedId, false otherwise
     */
    private static boolean isEmbeddedId(Class<?> entityClass, String fieldName) {
        try {
            Field field = entityClass.getDeclaredField(fieldName);
            return field.isAnnotationPresent(javax.persistence.EmbeddedId.class);
        } catch (NoSuchFieldException e) {
            // Try superclass
            Class<?> superClass = entityClass.getSuperclass();
            if (superClass != null && !superClass.equals(Object.class)) {
                return isEmbeddedId(superClass, fieldName);
            }
            return false;
        } catch (Exception e) {
            log.warn("Failed to check @EmbeddedId annotation for field {} in entity {}: {}", 
                fieldName, entityClass.getSimpleName(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets field names from an @EmbeddedId class.
     * 
     * @param entityClass the entity class
     * @param embeddedIdFieldName the name of the @EmbeddedId field
     * @return list of field names in the embedded ID class
     */
    private static List<String> getEmbeddedIdFieldNames(Class<?> entityClass, String embeddedIdFieldName) {
        List<String> fieldNames = new ArrayList<>();
        
        try {
            Field embeddedIdField = entityClass.getDeclaredField(embeddedIdFieldName);
            Class<?> embeddedIdClass = embeddedIdField.getType();
            
            // Get all fields from the embedded ID class
            for (Field field : embeddedIdClass.getDeclaredFields()) {
                // Skip static and synthetic fields
                if (!java.lang.reflect.Modifier.isStatic(field.getModifiers()) && 
                    !field.isSynthetic()) {
                    fieldNames.add(field.getName());
                }
            }
            
            log.debug("Found fields in @EmbeddedId class {}: {}", embeddedIdClass.getSimpleName(), fieldNames);
            
        } catch (Exception e) {
            log.warn("Failed to get fields from @EmbeddedId class for entity {}: {}", 
                entityClass.getSimpleName(), e.getMessage());
        }
        
        return fieldNames;
    }
    
    /**
     * Handles composite key detection for @IdClass entities.
     * Returns a special marker to indicate composite key processing is needed.
     */
    private static String handleCompositeKey(EntityType<?> entityType, Class<?> entityClass) {
        // For @IdClass composite keys, we return a special marker
        // This will be handled specially in TwoPhaseQueryExecutor
        return "__COMPOSITE_KEY__";
    }
    
    /**
     * Gets all ID field names for composite key entities (@IdClass and @EmbeddedId).
     * 
     * @param entityManager the entity manager
     * @param entityClass the entity class
     * @return list of ID field names for composite keys, empty list if single ID
     */
    public static List<String> getCompositeKeyFieldNames(EntityManager entityManager, Class<?> entityClass) {
        List<String> idFields = new ArrayList<>();
        
        try {
            EntityType<?> entityType = entityManager.getMetamodel().entity(entityClass);
            
            if (!entityType.hasSingleIdAttribute()) {
                // Get all ID attributes for @IdClass
                Set<? extends SingularAttribute<?, ?>> idAttributes = entityType.getIdClassAttributes();
                for (SingularAttribute<?, ?> idAttribute : idAttributes) {
                    idFields.add(idAttribute.getName());
                }
                log.debug("Found @IdClass composite key fields for {}: {}", entityClass.getSimpleName(), idFields);
            } else {
                // Check if it's @EmbeddedId
                SingularAttribute<?, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
                if (isEmbeddedId(entityClass, idAttribute.getName())) {
                    // For @EmbeddedId, get fields from the embedded class
                    idFields = getEmbeddedIdFieldNames(entityClass, idAttribute.getName());
                    log.debug("Found @EmbeddedId composite key fields for {}: {}", entityClass.getSimpleName(), idFields);
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to get composite key fields for entity {}: {}", entityClass.getSimpleName(), e.getMessage());
            // Fallback to reflection
            idFields = findIdFieldsByReflection(entityClass);
        }
        
        return idFields;
    }
    
    /**
     * Finds all @Id annotated fields using reflection (fallback method).
     */
    private static List<String> findIdFieldsByReflection(Class<?> entityClass) {
        List<String> idFields = new ArrayList<>();
        
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                idFields.add(field.getName());
            }
        }
        
        log.debug("Found ID fields by reflection for {}: {}", entityClass.getSimpleName(), idFields);
        return idFields;
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