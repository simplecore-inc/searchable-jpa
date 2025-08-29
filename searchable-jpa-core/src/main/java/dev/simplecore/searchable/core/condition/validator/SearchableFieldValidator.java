package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;

import jakarta.validation.ValidationException;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Validator for ensuring proper usage of {@link SearchableField} annotations in search conditions.
 * This validator checks three main aspects:
 * <ul>
 *   <li>Field existence: Verifies that all fields used in the search condition are properly annotated with @SearchableField</li>
 *   <li>Operator compatibility: Ensures that search operators used are allowed for each field</li>
 *   <li>Sort field validity: Confirms that fields used for sorting are marked as sortable</li>
 * </ul>
 *
 * <p>The validator supports inheritance by checking fields in the entire class hierarchy.
 *
 * <p>Example usage:
 * <pre>
 * SearchCondition condition = ...;
 * SearchableFieldValidator validator = new SearchableFieldValidator(UserDTO.class, condition);
 * validator.validate(); // throws ValidationException if validation fails
 * </pre>
 */
public class SearchableFieldValidator<D> {
    /**
     * The DTO class type that defines the searchable fields.
     */
    private final Class<D> dtoClass;

    /**
     * The search condition to validate.
     */
    private final SearchCondition<D> condition;

    /**
     * Cache of searchable fields and their annotations.
     */
    private final Map<String, SearchableField> searchableFields;

    /**
     * Cache of field names that are marked as sortable.
     */
    private final Set<String> sortableFields;

    /**
     * Creates a new validator for the specified DTO class and search condition.
     *
     * @param dtoClass  the DTO class that defines the searchable fields
     * @param condition the search condition to validate
     */
    public SearchableFieldValidator(Class<D> dtoClass, SearchCondition<D> condition) {
        this.dtoClass = dtoClass;
        this.condition = condition;
        this.searchableFields = buildSearchableFields();
        this.sortableFields = buildSortableFields();
    }

    /**
     * Validates that all fields used in the SearchCondition are properly annotated with @SearchableField
     * and their operators are allowed.
     *
     * @throws ValidationException if any validation fails, with a detailed error message containing:
     *                             - List of fields not annotated with @SearchableField
     *                             - List of operators not allowed for their respective fields
     *                             - List of fields not marked as sortable but used in sort conditions
     */
    public void validate() {
        // Skip validation if no DTO class is specified
        if (dtoClass == null) {
            return;
        }

        Set<String> invalidFields = new HashSet<>();
        Set<String> invalidOperators = new HashSet<>();
        Set<String> invalidSortFields = new HashSet<>();

        validateNodes(condition.getNodes(), invalidFields, invalidOperators);
        validateSort(condition.getSort(), invalidSortFields);

        // Build error message if any validation fails
        StringBuilder errorMessage = new StringBuilder();

        if (!invalidFields.isEmpty()) {
            errorMessage.append(MessageUtils.getMessage("validator.field.not.found",
                    new Object[]{String.join(", ", invalidFields), dtoClass.getSimpleName()}));
        }

        if (!invalidOperators.isEmpty()) {
            if (errorMessage.length() > 0) errorMessage.append("\n");
            errorMessage.append(MessageUtils.getMessage("validator.field.not.supported",
                    new Object[]{String.join(", ", invalidOperators)}));
        }

        if (!invalidSortFields.isEmpty()) {
            if (errorMessage.length() > 0) errorMessage.append("\n");
            errorMessage.append(MessageUtils.getMessage("validator.field.not.sortable",
                    new Object[]{String.join(", ", invalidSortFields)}));
        }

        if (errorMessage.length() > 0) {
            throw new SearchableValidationException(errorMessage.toString());
        }
    }

    /**
     * Builds a map of field names to their @SearchableField annotations.
     * Includes fields from the entire class hierarchy.
     *
     * @return map of field names to their SearchableField annotations
     */
    private Map<String, SearchableField> buildSearchableFields() {
        if (dtoClass == null) {
            return new HashMap<>();
        }

        Map<String, SearchableField> fields = new HashMap<>();
        Class<?> currentClass = dtoClass;

        while (currentClass != null && currentClass != Object.class) {
            for (Field field : currentClass.getDeclaredFields()) {
                SearchableField annotation = field.getAnnotation(SearchableField.class);
                if (annotation != null) {
                    fields.putIfAbsent(field.getName(), annotation);
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        return fields;
    }

    /**
     * Builds a set of field names that are marked as sortable.
     *
     * @return set of sortable field names
     */
    private Set<String> buildSortableFields() {
        return searchableFields.entrySet().stream()
                .filter(entry -> entry.getValue().sortable())
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Recursively validates nodes in the search condition.
     *
     * @param nodes            list of nodes to validate
     * @param invalidFields    set to collect fields that are not properly annotated
     * @param invalidOperators set to collect invalid operator usages
     */
    private void validateNodes(List<SearchCondition.Node> nodes, Set<String> invalidFields, Set<String> invalidOperators) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                validateCondition((SearchCondition.Condition) node, invalidFields, invalidOperators);
            } else if (node instanceof SearchCondition.Group) {
                validateNodes(node.getNodes(), invalidFields, invalidOperators);
            }
        }
    }

    /**
     * Validates a single condition node.
     *
     * @param condition        the condition to validate
     * @param invalidFields    set to collect fields that are not properly annotated
     * @param invalidOperators set to collect invalid operator usages
     */
    private void validateCondition(SearchCondition.Condition condition, Set<String> invalidFields, Set<String> invalidOperators) {
        String fieldName = condition.getField();
        SearchableField annotation = searchableFields.get(fieldName);

        if (annotation == null) {
            invalidFields.add(fieldName);
            return;
        }

        // Validate operator
        SearchOperator operator = condition.getSearchOperator();
        if (!isOperatorAllowed(operator, annotation.operators())) {
            invalidOperators.add(String.format("%s.%s", fieldName, operator));
        }
    }

    /**
     * Checks if a search operator is allowed for a field.
     *
     * @param operator         the operator to check
     * @param allowedOperators array of allowed operators
     * @return true if the operator is allowed, false otherwise
     */
    private boolean isOperatorAllowed(SearchOperator operator, SearchOperator[] allowedOperators) {
        return allowedOperators.length == 0 || Arrays.asList(allowedOperators).contains(operator);
    }

    /**
     * Validates sort conditions in the search condition.
     *
     * @param sort              the sort conditions to validate
     * @param invalidSortFields set to collect fields that are not properly marked as sortable
     */
    private void validateSort(SearchCondition.Sort sort, Set<String> invalidSortFields) {
        if (sort == null || sort.getOrders() == null) {
            return;
        }

        for (SearchCondition.Order order : sort.getOrders()) {
            String fieldName = order.getField();

            // Check if field exists and is sortable
            if (!sortableFields.contains(fieldName)) {
                invalidSortFields.add(fieldName);
            }
        }
    }
} 