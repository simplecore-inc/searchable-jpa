package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Collections;

@RequiredArgsConstructor
public class ExampleGenerator {
    private static final Logger log = LoggerFactory.getLogger(ExampleGenerator.class);

    public String generateSimpleExample(Class<?> dtoClass) {
        try {
            SearchCondition<?> condition = buildSimpleCondition(dtoClass);
            return condition.toJson();
        } catch (Exception e) {
            log.error("Error generating simple example: {}", e.getMessage(), e);
            return "{}";
        }
    }

    private SearchCondition<?> buildSimpleCondition(Class<?> dtoClass) {
        SearchConditionBuilder<?> builder = SearchConditionBuilder.create(dtoClass);

        builder.where(w -> {
            Field firstField = findFirstSearchableField(dtoClass);
            if (firstField != null) {
                processSearchableField(firstField, w);
            }
        });

        builder.page(0).size(10);
        return builder.build();
    }

    private void processSearchableField(Field field, FirstCondition w) {
        SearchableField searchableField = field.getAnnotation(SearchableField.class);
        if (searchableField != null && searchableField.operators().length > 0) {
            final Object rawExampleValue = OpenApiDocUtils.getExampleValue(field);
            if (rawExampleValue != null) {
                final Object exampleValue = convertToTargetType(rawExampleValue, field.getType());
                applyOperator(searchableField.operators()[0], exampleValue, field.getName(), w);
            }
        }
    }

    private void applyOperator(SearchOperator operator, Object exampleValue, String fieldName, FirstCondition w) {
        switch (operator) {
            case NOT_EQUALS:
                w.notEquals(fieldName, exampleValue);
                break;
            case GREATER_THAN:
                w.greaterThan(fieldName, exampleValue);
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                w.greaterThanOrEqualTo(fieldName, exampleValue);
                break;
            case LESS_THAN:
                w.lessThan(fieldName, exampleValue);
                break;
            case LESS_THAN_OR_EQUAL_TO:
                w.lessThanOrEqualTo(fieldName, exampleValue);
                break;
            case CONTAINS:
                w.contains(fieldName, String.valueOf(exampleValue));
                break;
            case NOT_CONTAINS:
                w.notContains(fieldName, String.valueOf(exampleValue));
                break;
            case STARTS_WITH:
                w.startsWith(fieldName, String.valueOf(exampleValue));
                break;
            case NOT_STARTS_WITH:
                w.notStartsWith(fieldName, String.valueOf(exampleValue));
                break;
            case ENDS_WITH:
                w.endsWith(fieldName, String.valueOf(exampleValue));
                break;
            case NOT_ENDS_WITH:
                w.notEndsWith(fieldName, String.valueOf(exampleValue));
                break;
            case IS_NULL:
                w.isNull(fieldName);
                break;
            case IS_NOT_NULL:
                w.isNotNull(fieldName);
                break;
            case IN:
                w.in(fieldName, Collections.singletonList(exampleValue));
                break;
            case NOT_IN:
                w.notIn(fieldName, Collections.singletonList(exampleValue));
                break;
            case BETWEEN:
                w.between(fieldName, exampleValue, exampleValue);
                break;
            case NOT_BETWEEN:
                w.notBetween(fieldName, exampleValue, exampleValue);
                break;
            case EQUALS:
            default:
                w.equals(fieldName, exampleValue);
        }
    }

    private Field findFirstSearchableField(Class<?> dtoClass) {
        for (Field field : dtoClass.getDeclaredFields()) {
            SearchableField searchableField = field.getAnnotation(SearchableField.class);
            if (searchableField != null) {
                return field;
            }
        }
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object convertToTargetType(Object rawExampleValue, Class<?> fieldType) {
        if (rawExampleValue instanceof String) {
            String strValue = (String) rawExampleValue;
            if (fieldType == Long.class || fieldType == long.class) {
                return Long.parseLong(strValue);
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return Integer.parseInt(strValue);
            } else if (fieldType == Double.class || fieldType == double.class) {
                return Double.parseDouble(strValue);
            } else if (fieldType == Float.class || fieldType == float.class) {
                return Float.parseFloat(strValue);
            } else if (fieldType == Short.class || fieldType == short.class) {
                return Short.parseShort(strValue);
            } else if (fieldType == Byte.class || fieldType == byte.class) {
                return Byte.parseByte(strValue);
            } else if (fieldType == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(strValue);
            } else if (fieldType == java.math.BigInteger.class) {
                return new java.math.BigInteger(strValue);
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                return Boolean.parseBoolean(strValue);
            } else if (fieldType == Character.class || fieldType == char.class) {
                return !strValue.isEmpty() ? strValue.charAt(0) : '\0';
            } else if (fieldType == java.time.LocalDateTime.class) {
                return java.time.LocalDateTime.parse(strValue);
            } else if (fieldType == java.time.LocalDate.class) {
                return java.time.LocalDate.parse(strValue);
            } else if (fieldType == java.time.LocalTime.class) {
                return java.time.LocalTime.parse(strValue);
            } else if (fieldType == java.time.ZonedDateTime.class) {
                return java.time.ZonedDateTime.parse(strValue);
            } else if (fieldType == java.time.OffsetDateTime.class) {
                return java.time.OffsetDateTime.parse(strValue);
            } else if (fieldType == java.time.Instant.class) {
                return java.time.Instant.parse(strValue);
            } else if (fieldType == java.util.Date.class) {
                return java.util.Date.from(java.time.Instant.parse(strValue));
            } else if (fieldType == java.sql.Date.class) {
                return java.sql.Date.valueOf(java.time.LocalDate.parse(strValue));
            } else if (fieldType == java.sql.Timestamp.class) {
                return java.sql.Timestamp.valueOf(java.time.LocalDateTime.parse(strValue));
            } else if (fieldType == java.util.UUID.class) {
                return java.util.UUID.fromString(strValue);
            } else if (fieldType.isEnum()) {
                return Enum.valueOf((Class) fieldType, strValue);
            }
        }
        return rawExampleValue;
    }
}
