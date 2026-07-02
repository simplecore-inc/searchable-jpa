package dev.simplecore.searchable.core.service.specification;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableOperationException;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.utils.JsonTypeDetector;
import dev.simplecore.searchable.core.utils.SearchableValueParser;
import lombok.NonNull;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PredicateBuilder<T> {
    private final CriteriaBuilder cb;
    private final JoinManager<T> joinManager;
    private String currentFieldPath;

    public PredicateBuilder(@NonNull CriteriaBuilder cb,
                            @NonNull JoinManager<T> joinManager) {
        this.cb = cb;
        this.joinManager = joinManager;
    }

    public Predicate build(SearchCondition.Node node) {
        if (node == null) {
            return null;
        }

        if (node instanceof SearchCondition.Condition) {
            SearchCondition.Condition condition = (SearchCondition.Condition) node;
            return buildPredicate(condition);
        }

        if (node instanceof SearchCondition.Group) {
            SearchCondition.Group group = (SearchCondition.Group) node;
            List<SearchCondition.Node> children = group.getNodes();
            if (children == null || children.isEmpty()) {
                return null;
            }

            // Fold children left-to-right by each child's own operator, matching
            // SpecificationBuilder.buildGroup so mixed AND/OR groups evaluate consistently.
            Predicate result = build(children.get(0));
            if (result == null) {
                return null;
            }
            for (int i = 1; i < children.size(); i++) {
                SearchCondition.Node child = children.get(i);
                Predicate currentPredicate = build(child);
                if (currentPredicate == null) {
                    continue;
                }
                result = (child.getOperator() == LogicalOperator.OR)
                        ? cb.or(result, currentPredicate)
                        : cb.and(result, currentPredicate);
            }
            return result;
        }

        throw new SearchableOperationException("Unknown node type: " + node.getClass().getSimpleName());
    }

    private Predicate buildPredicate(SearchCondition.Condition condition) {
        String entityField = condition.getEntityField();
        if (entityField == null || entityField.isEmpty()) {
            entityField = condition.getField();
        }

        if (entityField == null || entityField.isEmpty()) {
            throw new SearchableOperationException("Field must not be null or empty");
        }

        this.currentFieldPath = entityField;

        Path<?> path = joinManager.getJoinPath(entityField);
        SearchOperator operator = condition.getSearchOperator();
        Object value = condition.getValue();
        Object value2 = condition.getValue2();

        validateValueForOperator(path, value);
        return buildPredicateForOperator(path, operator, value, value2);
    }

    @SuppressWarnings({"unchecked"})
    private Predicate buildPredicateForOperator(Path<?> path, SearchOperator operator, Object value, Object value2) {
        Class<?> fieldType = path.getJavaType();
        Object convertedValue = convertValue(value, fieldType);

        switch (operator) {
            case EQUALS:
                return buildComparisonPredicate(path, convertedValue, cb::equal);
            case NOT_EQUALS:
                return buildComparisonPredicate(path, convertedValue, cb::notEqual);
            case GREATER_THAN:
                return buildComparablePredicate(path, convertedValue, (expr, val) -> cb.greaterThan(expr, val));
            case GREATER_THAN_OR_EQUAL_TO:
                return buildComparablePredicate(path, convertedValue, (expr, val) -> cb.greaterThanOrEqualTo(expr, val));
            case LESS_THAN:
                return buildComparablePredicate(path, convertedValue, (expr, val) -> cb.lessThan(expr, val));
            case LESS_THAN_OR_EQUAL_TO:
                return buildComparablePredicate(path, convertedValue, (expr, val) -> cb.lessThanOrEqualTo(expr, val));
            case BETWEEN:
                return buildBetweenPredicate(convertedValue, convertValue(value2, fieldType), path);
            case NOT_BETWEEN:
                return buildBetweenPredicate(convertedValue, convertValue(value2, fieldType), path).not();
            case CONTAINS:
                return buildStringPredicate(path, value, pattern -> "%" + pattern + "%", false);
            case NOT_CONTAINS:
                return buildStringPredicate(path, value, pattern -> "%" + pattern + "%", true);
            case STARTS_WITH:
                return buildStringPredicate(path, value, pattern -> pattern + "%", false);
            case NOT_STARTS_WITH:
                return buildStringPredicate(path, value, pattern -> pattern + "%", true);
            case ENDS_WITH:
                return buildStringPredicate(path, value, pattern -> "%" + pattern, false);
            case NOT_ENDS_WITH:
                return buildStringPredicate(path, value, pattern -> "%" + pattern, true);
            case IN:
                return buildInPredicate(path, value, false);
            case NOT_IN:
                return buildInPredicate(path, value, true);
            case IS_NULL:
                return cb.isNull(path);
            case IS_NOT_NULL:
                return cb.isNotNull(path);
            default:
                throw new SearchableOperationException("Unsupported operator: " + operator);
        }
    }

    private Predicate buildComparisonPredicate(Path<?> path, Object value, BiFunction<Expression<?>, Object, Predicate> operation) {
        return operation.apply(path, value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate buildComparablePredicate(Path<?> path, Object value, BiFunction<Expression<? extends Comparable>, Comparable, Predicate> operation) {
        Expression<? extends Comparable> comparablePath = getComparablePath(path);
        return operation.apply(comparablePath, (Comparable) value);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private Predicate buildBetweenPredicate(Object convertedValue1, Object convertedValue2, Path<?> path) {
        Expression<? extends Comparable> comparablePath = getComparablePath(path);
        try {
            return cb.between(comparablePath, (Comparable) convertedValue1, (Comparable) convertedValue2);
        } catch (Exception e) {
            throw new SearchableOperationException("Invalid values for between operation: " + convertedValue1 + " and " + convertedValue2, e);
        }
    }

    // Escape character used with LIKE so that escaped %/_ are matched literally on all databases.
    private static final char LIKE_ESCAPE_CHAR = '\\';

    private Predicate buildStringPredicate(Path<?> path, Object value, Function<String, String> patternBuilder, boolean negate) {
        if (value == null) {
            throw new SearchableOperationException("Pattern value must not be null for pattern matching operators");
        }
        String raw = value.toString();
        if (raw.isEmpty()) {
            throw new SearchableOperationException("Pattern value must not be empty for pattern matching operators");
        }
        Expression<String> stringPath = getStringPath(path);
        String pattern = patternBuilder.apply(escapePattern(raw));
        // Specify the ESCAPE character so the '\'-escaped %/_ are treated as literals (ANSI SQL:
        // without ESCAPE, the backslash is not recognized as an escape on many databases).
        Predicate predicate = cb.like(stringPath, pattern, LIKE_ESCAPE_CHAR);
        return negate ? predicate.not() : predicate;
    }

    private Predicate buildInPredicate(Path<?> path, Object value, boolean negate) {
        Collection<?> values = validateCollectionValue(value);
        Class<?> fieldType = path.getJavaType();

        Collection<Object> convertedValues = values.stream()
                .map(v -> convertValue(v, fieldType))
                .collect(Collectors.toList());

        Predicate predicate = path.in(convertedValues);
        return negate ? predicate.not() : predicate;
    }

    private Collection<?> validateCollectionValue(Object value) {
        Collection<?> collection;
        if (value instanceof Collection) {
            collection = (Collection<?>) value;
        } else if (value instanceof Object[]) {
            collection = Arrays.asList((Object[]) value);
        } else {
            throw new SearchableOperationException("Value must be a collection or array");
        }

        if (collection.isEmpty()) {
            throw new SearchableOperationException("Collection must have at least 1 items");
        }
        return collection;
    }

    @SuppressWarnings("unchecked")
    private <Y extends Comparable<? super Y>> Expression<Y> getComparablePath(Path<?> path) {
        Class<?> type = path.getJavaType();
        if (!Comparable.class.isAssignableFrom(type)) {
            throw new SearchableOperationException("Field type " + type.getSimpleName() + " must implement Comparable for comparison operations");
        }
        return (Expression<Y>) path;
    }

    private Expression<String> getStringPath(Path<?> path) {
        Class<?> type = path.getJavaType();

        if (String.class.equals(type)) {
            return path.as(String.class);
        }

        if (currentFieldPath != null &&
            JsonTypeDetector.isJsonTypedField(
                joinManager.getEntityManager(),
                joinManager.getEntityClass(),
                currentFieldPath)) {
            return path.as(String.class);
        }

        throw new SearchableOperationException(
            "Field must be String or JSON-typed for pattern matching operators");
    }

    private String escapePattern(String value) {
        // Escape the escape character itself first, then the LIKE wildcards.
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void validateValueForOperator(Path<?> path, Object value) {
        if (value == null) {
            return;
        }

        Class<?> fieldType = path.getJavaType();

        if (value instanceof Collection) {
            validateCollectionValues((Collection<?>) value, fieldType);
        } else {
            validateSingleValue(value, fieldType);
        }
    }

    private void validateCollectionValues(Collection<?> values, Class<?> fieldType) {
        for (Object item : values) {
            if (item != null) {
                validateSingleValue(item, fieldType);
            }
        }
    }

    private void validateSingleValue(Object value, Class<?> fieldType) {
        try {
            convertValue(value, fieldType);
        } catch (Exception e) {
            throw new SearchableOperationException("Invalid value '" + value + "' for field type " + fieldType.getSimpleName(), e);
        }
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        // If value is already of target type, return as is
        if (targetType.isInstance(value)) {
            return value;
        }

        // If value is String, use SearchableValueParser
        if (value instanceof String) {
            return SearchableValueParser.parseValue((String) value, targetType);
        }

        // If value is Collection, convert each element
        if (value instanceof Collection) {
            Collection<?> collection = (Collection<?>) value;
            return collection.stream()
                    .map(item -> item instanceof String ?
                            SearchableValueParser.parseValue((String) item, targetType) : item)
                    .collect(Collectors.toList());
        }

        // If value is array, convert to List and handle each element
        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            return Arrays.stream(array)
                    .map(item -> item instanceof String ?
                            SearchableValueParser.parseValue((String) item, targetType) : item)
                    .collect(Collectors.toList());
        }

        throw new SearchableOperationException(
                String.format("Cannot convert value of type %s to %s",
                        value.getClass().getSimpleName(),
                        targetType.getSimpleName())
        );
    }
} 