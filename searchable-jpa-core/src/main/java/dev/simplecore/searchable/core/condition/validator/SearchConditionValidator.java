package dev.simplecore.searchable.core.condition.validator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableValidationException;
import dev.simplecore.searchable.core.i18n.MessageUtils;
import dev.simplecore.searchable.core.utils.SearchableValueParser;

import jakarta.validation.*;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Validator for search conditions that ensures all search criteria are valid and consistent.
 * This validator checks various aspects of a search condition including:
 * <ul>
 *   <li>Pagination parameters validity</li>
 *   <li>Field existence and searchability</li>
 *   <li>Operator compatibility with field types</li>
 *   <li>Value type compatibility and constraints</li>
 *   <li>Collection values for IN/NOT_IN operators</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>
 * SearchCondition condition = ...;
 * SearchConditionValidator validator = new SearchConditionValidator(UserDTO.class, condition);
 * validator.validate(); // throws ValidationException if any validation fails
 * </pre>
 */
public class SearchConditionValidator<D> {
    /**
     * Factory for creating validators for bean validation.
     */
    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();

    /**
     * Validator instance for bean validation.
     */
    private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

    /**
     * The DTO class type that defines the searchable fields.
     */
    private final Class<?> dtoClass;

    /**
     * The search condition to validate.
     */
    private final SearchCondition<D> condition;

    /**
     * Creates a new validator for the specified DTO class and search condition.
     *
     * @param dtoClass  the DTO class that defines the searchable fields
     * @param condition the search condition to validate
     */
    public SearchConditionValidator(Class<?> dtoClass, SearchCondition<D> condition) {
        this.dtoClass = dtoClass;
        this.condition = condition;
    }

    /**
     * Validates the entire search condition.
     * This includes pagination parameters, field existence, operator compatibility,
     * and value type compatibility.
     *
     * @throws ValidationException if any validation fails
     */
    public void validate() {
        validatePagination();
        validateNodes(condition.getNodes());
    }

    /**
     * Validates pagination parameters if they are present.
     * Both page and size must be set together, and must be valid numbers.
     *
     * @throws ValidationException if pagination parameters are invalid
     */
    private void validatePagination() {
        if (condition.getPage() != null ^ condition.getSize() != null) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.pagination.both.required"));
        }
        if (condition.getPage() != null && condition.getPage() < 0) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.pagination.page.invalid"));
        }
        if (condition.getSize() != null && condition.getSize() <= 0) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.pagination.size.invalid"));
        }
    }

    /**
     * Recursively validates a list of search condition nodes.
     * Handles both individual conditions and nested condition groups.
     *
     * @param nodes the list of nodes to validate
     * @throws ValidationException if any node fails validation
     */
    private void validateNodes(List<SearchCondition.Node> nodes) {
        if (nodes == null) return;

        for (SearchCondition.Node node : nodes) {
            if (node instanceof SearchCondition.Condition) {
                validateCondition((SearchCondition.Condition) node);
            } else if (node instanceof SearchCondition.Group) {
                validateNodes(node.getNodes());
            }
        }
    }

    /**
     * Validates a single search condition.
     * Checks field existence, operator compatibility, and value type compatibility.
     *
     * @param condition the condition to validate
     * @throws ValidationException if the condition is invalid
     */
    private void validateCondition(SearchCondition.Condition condition) {
        String fieldName = condition.getField();
        PropertyDescriptor pd = findPropertyDescriptor(fieldName);

        validateField(pd, fieldName);
        validateOperator(condition.getSearchOperator(), fieldName);
        validateValue(pd.getPropertyType(), condition.getValue(), condition.getSearchOperator(), fieldName);

        // Validate constraints
        try {
            Object tempObject = dtoClass.getDeclaredConstructor().newInstance();
            java.lang.reflect.Field field = dtoClass.getDeclaredField(fieldName);
            field.setAccessible(true);

            // For IN/NOT_IN operators, validate each value individually
            if (condition.getValue() instanceof Collection &&
                    (condition.getSearchOperator() == SearchOperator.IN ||
                            condition.getSearchOperator() == SearchOperator.NOT_IN)) {
                for (Object value : (Collection<?>) condition.getValue()) {
                    Object convertedValue = value instanceof String
                            ? SearchableValueParser.parseValue((String) value, field.getType())
                            : value;
                    field.set(tempObject, convertedValue);
                    Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(tempObject);
                    if (!violations.isEmpty()) {
                        throw new SearchableValidationException(violations.iterator().next().getMessage());
                    }
                }
            } else {
                Object convertedValue = condition.getValue() instanceof String
                        ? SearchableValueParser.parseValue((String) condition.getValue(), field.getType())
                        : condition.getValue();
                field.set(tempObject, convertedValue);
                Set<ConstraintViolation<Object>> violations = VALIDATOR.validate(tempObject);
                if (!violations.isEmpty()) {
                    throw new SearchableValidationException(violations.iterator().next().getMessage());
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.field.validation.failed", new Object[]{fieldName}));
        }
    }

    /**
     * Finds the property descriptor for a field in the DTO class.
     *
     * @param fieldName the name of the field
     * @return the property descriptor for the field
     * @throws ValidationException if the field cannot be found
     */
    private PropertyDescriptor findPropertyDescriptor(String fieldName) {
        try {
            return Arrays.stream(Introspector.getBeanInfo(dtoClass).getPropertyDescriptors())
                    .filter(pd -> pd.getName().equals(fieldName))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.property.not.found", new Object[]{fieldName}));
        }
    }

    /**
     * Validates that a field exists and is marked as searchable.
     *
     * @param pd        the property descriptor of the field
     * @param fieldName the name of the field
     * @throws ValidationException if the field is not valid for searching
     */
    private void validateField(PropertyDescriptor pd, String fieldName) {
        if (pd == null) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.found", new Object[]{fieldName}));
        }

        try {
            SearchableField annotation = dtoClass.getDeclaredField(fieldName).getAnnotation(SearchableField.class);
            if (annotation == null) {
                throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.searchable", new Object[]{fieldName}));
            }
        } catch (NoSuchFieldException e) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.found", new Object[]{fieldName}));
        }
    }

    /**
     * Validates that an operator is allowed for a field.
     *
     * @param operator  the search operator to validate
     * @param fieldName the name of the field
     * @throws ValidationException if the operator is not allowed for the field
     */
    private void validateOperator(SearchOperator operator, String fieldName) {
        try {
            SearchableField annotation = dtoClass.getDeclaredField(fieldName).getAnnotation(SearchableField.class);
            if (annotation == null) {
                throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.searchable", new Object[]{fieldName}));
            }
            if (!Arrays.asList(annotation.operators()).contains(operator)) {
                throw new SearchableValidationException(
                        MessageUtils.getMessage("validator.operator.not.allowed",
                                new Object[]{operator, fieldName, Arrays.toString(annotation.operators())})
                );
            }
        } catch (NoSuchFieldException e) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.field.not.found", new Object[]{fieldName}));
        }
    }

    /**
     * Validates a search value against a field's type and operator.
     *
     * @param targetType the expected type of the value
     * @param value      the value to validate
     * @param operator   the search operator being used
     * @param fieldName  the name of the field
     * @throws ValidationException if the value is not valid for the field and operator
     */
    private void validateValue(Class<?> targetType, Object value, SearchOperator operator, String fieldName) {
        // Validate null value
        if (value == null) {
            if (operator != SearchOperator.IS_NULL && operator != SearchOperator.IS_NOT_NULL) {
                throw new SearchableValidationException(MessageUtils.getMessage("validator.value.null.not.allowed", new Object[]{operator}));
            }
            return;
        }

        // Validate IN/NOT_IN operators
        if (operator == SearchOperator.IN || operator == SearchOperator.NOT_IN) {
            validateCollectionValue(targetType, value, fieldName);
            return;
        }

        // Validate single value
        validateSingleValue(targetType, value, fieldName);
    }

    /**
     * Validates a collection value for IN/NOT_IN operators.
     *
     * @param targetType the expected type of the collection elements
     * @param value      the collection to validate
     * @param fieldName  the name of the field
     * @throws ValidationException if the collection or its elements are not valid
     */
    private void validateCollectionValue(Class<?> targetType, Object value, String fieldName) {
        if (!(value instanceof Collection)) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.must.be.collection", new Object[]{fieldName, value.getClass().getSimpleName()})
            );
        }

        Collection<?> collection = (Collection<?>) value;
        if (collection.isEmpty()) {
            throw new SearchableValidationException(MessageUtils.getMessage("validator.collection.empty", new Object[]{fieldName}));
        }

        for (Object item : collection) {
            validateSingleValue(targetType, item, fieldName);
        }
    }

    /**
     * Validates a single value against a field's type.
     * Handles type conversion for various data types including numbers, dates, and enums.
     *
     * @param targetType the expected type of the value
     * @param value      the value to validate
     * @param fieldName  the name of the field
     * @throws ValidationException if the value cannot be converted to the target type
     */
    private void validateSingleValue(Class<?> targetType, Object value, String fieldName) {
        try {
            if (targetType.isInstance(value)) {
                return;
            }

            if (value instanceof String) {
                SearchableValueParser.parseValue((String) value, targetType);
                return;
            }

            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.type.conversion",
                            new Object[]{value.getClass().getSimpleName(), targetType.getSimpleName(), fieldName})
            );
        } catch (Exception e) {
            throw new SearchableValidationException(
                    MessageUtils.getMessage("validator.value.invalid", new Object[]{fieldName, e.getMessage()})
            );
        }
    }
} 