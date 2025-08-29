package dev.simplecore.searchable.openapi.utils;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Pattern;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

public class OpenApiDocUtils {

    private OpenApiDocUtils() {
        throw new IllegalStateException("Utility class");
    }

    public static Object getExampleValue(Field field) {
        Schema schema = field.getAnnotation(Schema.class);
        if (schema != null && !schema.example().isEmpty()) {
            return schema.example();
        }
        return getDefaultExampleValue(field);
    }

    public static Object getExampleValue(Field field, SearchOperator operator) {
        if (operator == SearchOperator.BETWEEN) {
            return getBetweenExampleValue(field);
        } else if (isInOperation(operator)) {
            return getInExampleValue(field);
        }
        return getExampleValue(field);
    }

    private static boolean isInOperation(SearchOperator operator) {
        return operator == SearchOperator.IN || operator == SearchOperator.NOT_IN;
    }

    private static Object getBetweenExampleValue(Field field) {
        if (field.getType() == LocalDateTime.class) {
            List<Object> objects = new java.util.ArrayList<>();
            objects.add(getExampleValue(field));
            objects.add(getExampleValue(field));
            return objects;
        }
        List<Integer> integers = new java.util.ArrayList<>();
        integers.add(1);
        integers.add(100);
        return integers;
    }

    private static Object getInExampleValue(Field field) {
        if (field.isAnnotationPresent(Pattern.class)) {
            Pattern patternAnnotation = field.getAnnotation(Pattern.class);
            if ("^[YN]$".equals(patternAnnotation.regexp())) {
                List<String> strings = new java.util.ArrayList<>();
                strings.add("Y");
                strings.add("N");
                return strings;
            }
        }
        List<Object> objects = new java.util.ArrayList<>();
        objects.add(getExampleValue(field));
        return objects;
    }

    private static Object getDefaultExampleValue(Field field) {
        Class<?> type = field.getType();
        if (type == String.class) return "example";
        if (type == Integer.class || type == int.class) return 1;
        if (type == Long.class || type == long.class) return 1L;
        if (type == Double.class || type == double.class) return 1.0;
        if (type == Float.class || type == float.class) return 1.0f;
        if (type == Boolean.class || type == boolean.class) return true;
        if (type == LocalDateTime.class) return LocalDateTime.now();
        if (type.isEnum()) return type.getEnumConstants()[0];
        return null;
    }

    public static String toCamelCase(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String[] words = input.split("[\\W_]+");
        StringBuilder builder = new StringBuilder();
        builder.append(words[0].toLowerCase());
        for (int i = 1; i < words.length; i++) {
            if (!words[i].isEmpty()) {
                builder.append(Character.toUpperCase(words[i].charAt(0)))
                        .append(words[i].substring(1).toLowerCase());
            }
        }
        return builder.toString();
    }

    public static String getOperationDescription(SearchOperator operator) {
        return operator.getName();
    }
}