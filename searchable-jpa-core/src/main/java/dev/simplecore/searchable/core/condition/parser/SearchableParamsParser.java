package dev.simplecore.searchable.core.condition.parser;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.builder.FirstCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableParseException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import lombok.NonNull;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser for converting URL parameters into SearchCondition objects.
 * Supports parsing of field conditions, sorting, and pagination parameters.
 *
 * <p>Format:
 * - Field conditions: field.operator=value or field.operator=value1,value2
 * - Sort: sort=field.asc,field.desc
 * - Pagination: page=0&size=10
 *
 * <p>Example:
 * name.equals=John&age.greaterThan=20&sort=name.asc,age.desc&page=0&size=10
 *
 * @param <D>
 */
public class SearchableParamsParser<D> {
    private static final String FIELD_OPERATOR_PATTERN = "^([^.]+(?:\\.[^.]+)*)\\.([^=]+)$";
    private static final String SORT_DIRECTION_PATTERN = "^([^.]+(?:\\.[^.]+)*?)\\.(asc|desc)$";
    private static final Pattern fieldOperatorPattern = Pattern.compile(FIELD_OPERATOR_PATTERN);
    private static final Pattern sortDirectionPattern = Pattern.compile(SORT_DIRECTION_PATTERN);

    private final Class<D> dtoClass;

    public SearchableParamsParser(@NonNull Class<D> dtoClass) {
        this.dtoClass = dtoClass;
    }

    /**
     * Converts URL parameters into a SearchCondition.
     *
     * @param params Map of URL parameters
     * @return SearchCondition object
     * @throws SearchableParseException if parsing fails
     */
    public SearchCondition<D> convert(@NonNull Map<String, String> params) {
        try {
            SearchConditionBuilder<D> builder = SearchConditionBuilder.create(dtoClass);

            // Parse conditions
            Map<String, String> conditions = new HashMap<>();

            params.forEach((key, value) -> {
                switch (key) {
                    case "sort":
                        parseSort(builder, value);
                        break;
                    case "page":
                        validateNumeric(value, "page");
                        builder.page(Integer.parseInt(value));
                        break;
                    case "size":
                        validateNumeric(value, "size");
                        builder.size(Integer.parseInt(value));
                        break;
                    default:
                        conditions.put(key, value);
                        break;
                }
            });

            // Parse conditions (AND)
            if (!conditions.isEmpty()) {
                builder.where(whereBuilder ->
                        conditions.forEach((key, value) ->
                                parseCondition(whereBuilder, key, value)));
            }

            return builder.build();
        } catch (Exception e) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.parse.error"), e);
        }
    }

    private void validateNumeric(String value, String field) {
        try {
            int num = Integer.parseInt(value);
            if (num < 0) {
                throw new SearchableParseException(MessageUtils.getMessage("parser.numeric.nonnegative", new Object[]{field}));
            }
        } catch (NumberFormatException e) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.numeric.invalid", new Object[]{field}));
        }
    }

    private void parseSort(SearchConditionBuilder<D> builder, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }

        builder.sort(sortBuilder -> {
            String[] sortOrders = value.split(",");
            for (String order : sortOrders) {
                Matcher matcher = sortDirectionPattern.matcher(order.trim());
                if (matcher.matches()) {
                    String field = matcher.group(1);
                    String direction = matcher.group(2);
                    if ("asc".equals(direction)) {
                        sortBuilder.asc(field);
                    } else {
                        sortBuilder.desc(field);
                    }
                } else {
                    throw new SearchableParseException(MessageUtils.getMessage("parser.sort.invalid", new Object[]{order}));
                }
            }
        });
    }

    private void parseCondition(FirstCondition whereBuilder, String key, String value) {
        Matcher matcher = fieldOperatorPattern.matcher(key);
        if (!matcher.matches()) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.field.invalid", new Object[]{key}));
        }

        String field = matcher.group(1);
        String operatorStr = matcher.group(2);

        try {
            SearchOperator operator = SearchOperator.fromName(operatorStr);
            String[] values = value.split(",");

            switch (Objects.requireNonNull(operator)) {
                case EQUALS:
                    whereBuilder.equals(field, parseValue(field, values[0]));
                    break;
                case NOT_EQUALS:
                    whereBuilder.notEquals(field, parseValue(field, values[0]));
                    break;
                case GREATER_THAN:
                    whereBuilder.greaterThan(field, parseValue(field, values[0]));
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    whereBuilder.greaterThanOrEqualTo(field, parseValue(field, values[0]));
                    break;
                case LESS_THAN:
                    whereBuilder.lessThan(field, parseValue(field, values[0]));
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    whereBuilder.lessThanOrEqualTo(field, parseValue(field, values[0]));
                    break;
                case CONTAINS:
                    whereBuilder.contains(field, values[0]);
                    break;
                case NOT_CONTAINS:
                    whereBuilder.notContains(field, values[0]);
                    break;
                case STARTS_WITH:
                    whereBuilder.startsWith(field, values[0]);
                    break;
                case NOT_STARTS_WITH:
                    whereBuilder.notStartsWith(field, values[0]);
                    break;
                case ENDS_WITH:
                    whereBuilder.endsWith(field, values[0]);
                    break;
                case NOT_ENDS_WITH:
                    whereBuilder.notEndsWith(field, values[0]);
                    break;
                case IN:
                    whereBuilder.in(field, Arrays.asList(values));
                    break;
                case NOT_IN:
                    whereBuilder.notIn(field, Arrays.asList(values));
                    break;
                case BETWEEN:
                    if (values.length != 2) {
                        throw new SearchableParseException(MessageUtils.getMessage("parser.operator.requires.two.values", new Object[]{operatorStr}));
                    }
                    whereBuilder.between(field, parseValue(field, values[0]), parseValue(field, values[1]));
                    break;
                case NOT_BETWEEN:
                    if (values.length != 2) {
                        throw new SearchableParseException(MessageUtils.getMessage("parser.operator.requires.two.values", new Object[]{operatorStr}));
                    }
                    whereBuilder.notBetween(field, parseValue(field, values[0]), parseValue(field, values[1]));
                    break;
                case IS_NULL:
                    whereBuilder.isNull(field);
                    break;
                case IS_NOT_NULL:
                    whereBuilder.isNotNull(field);
                    break;
                default:
                    throw new SearchableParseException("Unsupported operator: " + operatorStr);
            }
        } catch (IllegalArgumentException e) {
            throw new SearchableParseException(MessageUtils.getMessage("parser.operator.invalid", new Object[]{operatorStr}), e);
        }
    }

    private Object parseValue(String field, String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            Class<?> fieldType = getFieldType(field);
            return SearchableValueParser.parseValue(value, fieldType);
        } catch (Exception e) {
            throw new SearchableParseException("Failed to parse value for field: " + field, e);
        }
    }

    private Class<?> getFieldType(String field) {
        try {
            return dtoClass.getDeclaredField(field).getType();
        } catch (NoSuchFieldException e) {
            // Field might be a nested property or not exist
            return String.class; // Default to String if field type cannot be determined
        }
    }
}
