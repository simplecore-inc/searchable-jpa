package dev.simplecore.searchable.core.exception;

/**
 * Exception thrown when parsing search parameters fails.
 * <p>
 * Extends {@link SearchableException} so that all parsing failures can be caught
 * together with other Searchable JPA errors via {@code catch (SearchableException e)}.
 */
public class SearchableParseException extends SearchableException {
    public SearchableParseException(String message) {
        super(message);
    }

    public SearchableParseException(String message, Throwable cause) {
        super(message, cause);
    }
} 