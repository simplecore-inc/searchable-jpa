package dev.simplecore.searchable.core.exception;

import javax.validation.ValidationException;

/**
 * Exception thrown when there is a validation error in search conditions.
 * This includes field validation errors, operator validation errors, and value type validation issues.
 */
public class SearchableValidationException extends ValidationException {

    public SearchableValidationException(String message) {
        super(message);
    }

    public SearchableValidationException(String message, Throwable cause) {
        super(message, cause);
    }
} 