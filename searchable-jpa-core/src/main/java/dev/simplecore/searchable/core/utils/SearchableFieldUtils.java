package dev.simplecore.searchable.core.utils;

import dev.simplecore.searchable.core.annotation.SearchableField;

public class SearchableFieldUtils {
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
} 