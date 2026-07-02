package dev.simplecore.searchable.core.exception;

/**
 * Exception thrown when there is a validation error in search conditions.
 * This includes field validation errors, operator validation errors, and value type validation issues.
 * <p>
 * Extends {@link SearchableException} so that validation failures can be caught together with
 * all other Searchable JPA errors via {@code catch (SearchableException e)}. Note that this
 * exception no longer extends {@link jakarta.validation.ValidationException}.
 */
public class SearchableValidationException extends SearchableException {

    public SearchableValidationException(String message) {
        super(message);
    }

    public SearchableValidationException(String message, Throwable cause) {
        super(message, cause);
    }
} 