package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.openapi.utils.OpenApiDocUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

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
            applyOperator(searchableField.operators()[0], field, w);
        }
    }

    private void applyOperator(SearchOperator operator, Field field, FirstCondition w) {
        String fieldName = field.getName();
        switch (operator) {
            case BETWEEN: {
                List<Object> range = exampleRange(field, operator);
                w.between(fieldName, range.get(0), range.get(1));
                break;
            }
            case NOT_BETWEEN: {
                List<Object> range = exampleRange(field, operator);
                w.notBetween(fieldName, range.get(0), range.get(1));
                break;
            }
            case IN:
                w.in(fieldName, exampleValues(field, operator));
                break;
            case NOT_IN:
                w.notIn(fieldName, exampleValues(field, operator));
                break;
            default:
                applySingleValueOperator(operator, field, w);
        }
    }

    private void applySingleValueOperator(SearchOperator operator, Field field, FirstCondition w) {
        String fieldName = field.getName();
        Object rawExampleValue = OpenApiDocUtils.getExampleValue(field);
        Object exampleValue = rawExampleValue == null ? null : convertToTargetType(rawExampleValue, field.getType());
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
            case EQUALS:
            default:
                w.equals(fieldName, exampleValue);
        }
    }

    /**
     * Produces the two distinct bounds for a BETWEEN/NOT_BETWEEN example using the 2-arg example
     * generator, converting each to the field's type.
     */
    private List<Object> exampleRange(Field field, SearchOperator operator) {
        List<Object> converted = exampleValues(field, operator);
        while (converted.size() < 2) {
            converted.add(convertToTargetType(OpenApiDocUtils.getExampleValue(field), field.getType()));
        }
        return converted;
    }

    /**
     * Produces the example values for a multi-value operator (IN/NOT_IN/BETWEEN) using the 2-arg
     * example generator, converting each element to the field's type.
     */
    private List<Object> exampleValues(Field field, SearchOperator operator) {
        Object example = OpenApiDocUtils.getExampleValue(field, operator);
        List<Object> values = new ArrayList<>();
        if (example instanceof List) {
            for (Object element : (List<?>) example) {
                values.add(convertToTargetType(element, field.getType()));
            }
        } else if (example != null) {
            values.add(convertToTargetType(example, field.getType()));
        }
        return values;
    }

    private Field findFirstSearchableField(Class<?> dtoClass) {
        List<Field> searchableFields = SearchableFieldUtils.getSearchableFields(dtoClass);
        return searchableFields.isEmpty() ? null : searchableFields.get(0);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Object convertToTargetType(Object rawExampleValue, Class<?> fieldType) {
        // Convert numeric example values (e.g. the generic [1, 100] BETWEEN range) to the field's type.
        if (rawExampleValue instanceof Number && !fieldType.isInstance(rawExampleValue)) {
            Number number = (Number) rawExampleValue;
            if (fieldType == Long.class || fieldType == long.class) {
                return number.longValue();
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return number.intValue();
            } else if (fieldType == Double.class || fieldType == double.class) {
                return number.doubleValue();
            } else if (fieldType == Float.class || fieldType == float.class) {
                return number.floatValue();
            } else if (fieldType == Short.class || fieldType == short.class) {
                return number.shortValue();
            } else if (fieldType == Byte.class || fieldType == byte.class) {
                return number.byteValue();
            } else if (fieldType == java.math.BigDecimal.class) {
                return new java.math.BigDecimal(number.toString());
            } else if (fieldType == java.math.BigInteger.class) {
                return java.math.BigInteger.valueOf(number.longValue());
            }
        }
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
