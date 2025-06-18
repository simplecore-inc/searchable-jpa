package dev.simplecore.searchable.core.exception;

/**
 * Exception thrown when there is an error in join operations.
 * This includes invalid join paths, join creation failures, and join cache issues.
 */
public class SearchableJoinException extends SearchableException {

    public SearchableJoinException(String message) {
        super(message);
    }

    public SearchableJoinException(String message, Throwable cause) {
        super(message, cause);
    }
} 