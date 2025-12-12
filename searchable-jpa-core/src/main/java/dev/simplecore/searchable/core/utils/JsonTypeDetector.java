package dev.simplecore.searchable.core.utils;

import jakarta.persistence.Column;
import jakarta.persistence.EntityManager;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to detect JSON-typed fields in JPA entities.
 * JSON-typed fields are stored as TEXT/VARCHAR in the database but represented
 * as complex Java types (Map, List) in the entity.
 */
public final class JsonTypeDetector {

    private static final Map<String, Boolean> TYPE_CACHE = new ConcurrentHashMap<>();

    private JsonTypeDetector() {
    }

    /**
     * Checks if a field is JSON-typed (stored as TEXT/VARCHAR in DB but complex type in Java).
     *
     * @param entityManager The JPA EntityManager
     * @param entityClass   The entity class
     * @param fieldPath     The field path (supports nested paths like "author.metadata")
     * @return true if the field is JSON-typed
     */
    public static boolean isJsonTypedField(EntityManager entityManager,
                                           Class<?> entityClass,
                                           String fieldPath) {
        if (entityClass == null || fieldPath == null || fieldPath.isEmpty()) {
            return false;
        }

        String cacheKey = entityClass.getName() + "#" + fieldPath;
        return TYPE_CACHE.computeIfAbsent(cacheKey, k -> detectJsonType(entityClass, fieldPath));
    }

    private static boolean detectJsonType(Class<?> entityClass, String fieldPath) {
        String[] pathParts = fieldPath.split("\\.");
        Class<?> currentClass = entityClass;

        for (int i = 0; i < pathParts.length; i++) {
            String fieldName = pathParts[i];
            Field field = findField(currentClass, fieldName);

            if (field == null) {
                return false;
            }

            if (i == pathParts.length - 1) {
                return isJsonField(field);
            }

            currentClass = field.getType();
        }

        return false;
    }

    private static boolean isJsonField(Field field) {
        Class<?> fieldType = field.getType();

        if (!isComplexType(fieldType)) {
            return false;
        }

        if (hasHibernateJsonType(field)) {
            return true;
        }

        if (hasJpaJsonConverter(field)) {
            return true;
        }

        if (hasJdbcTypeCodeJson(field)) {
            return true;
        }

        if (hasJsonColumnDefinition(field)) {
            return true;
        }

        return false;
    }

    private static boolean isComplexType(Class<?> type) {
        return Map.class.isAssignableFrom(type) ||
               Collection.class.isAssignableFrom(type) ||
               type.isArray();
    }

    private static boolean hasHibernateJsonType(Field field) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> typeAnnotationClass =
                (Class<? extends Annotation>) Class.forName("org.hibernate.annotations.Type");

            Annotation typeAnnotation = field.getAnnotation(typeAnnotationClass);
            if (typeAnnotation != null) {
                Method valueMethod = typeAnnotationClass.getMethod("value");
                Class<?> typeClass = (Class<?>) valueMethod.invoke(typeAnnotation);
                String typeName = typeClass.getName().toLowerCase();
                return typeName.contains("json");
            }
        } catch (ClassNotFoundException e) {
            // Hibernate annotations not in classpath
        } catch (Exception e) {
            // Annotation not present or error reading it
        }
        return false;
    }

    private static boolean hasJpaJsonConverter(Field field) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> convertAnnotationClass =
                (Class<? extends Annotation>) Class.forName("jakarta.persistence.Convert");

            Annotation convertAnnotation = field.getAnnotation(convertAnnotationClass);
            if (convertAnnotation != null) {
                Method converterMethod = convertAnnotationClass.getMethod("converter");
                Class<?> converterClass = (Class<?>) converterMethod.invoke(convertAnnotation);
                String converterName = converterClass.getName().toLowerCase();
                return converterName.contains("json");
            }
        } catch (ClassNotFoundException e) {
            // JPA Convert not available
        } catch (Exception e) {
            // Annotation not present or error reading it
        }
        return false;
    }

    private static boolean hasJdbcTypeCodeJson(Field field) {
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Annotation> jdbcTypeCodeClass =
                (Class<? extends Annotation>) Class.forName("org.hibernate.annotations.JdbcTypeCode");

            Annotation jdbcTypeCodeAnnotation = field.getAnnotation(jdbcTypeCodeClass);
            if (jdbcTypeCodeAnnotation != null) {
                Method valueMethod = jdbcTypeCodeClass.getMethod("value");
                int typeCode = (int) valueMethod.invoke(jdbcTypeCodeAnnotation);

                // SqlTypes.JSON = 1111
                // SqlTypes.LONGVARCHAR = -1
                // SqlTypes.LONGNVARCHAR = -16
                return typeCode == 1111 || typeCode == -1 || typeCode == -16;
            }
        } catch (ClassNotFoundException e) {
            // Hibernate 6 annotations not available
        } catch (Exception e) {
            // Annotation not present or error reading it
        }
        return false;
    }

    private static boolean hasJsonColumnDefinition(Field field) {
        Column columnAnnotation = field.getAnnotation(Column.class);
        if (columnAnnotation != null) {
            String colDef = columnAnnotation.columnDefinition().toLowerCase();
            return colDef.contains("json") || colDef.contains("text");
        }
        return false;
    }

    private static Field findField(Class<?> clazz, String fieldName) {
        Class<?> current = clazz;
        while (current != null && !current.equals(Object.class)) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * Clears the type detection cache.
     * Useful for testing purposes.
     */
    public static void clearCache() {
        TYPE_CACHE.clear();
    }
}