package dev.simplecore.searchable.core.exception;

/**
 * Base exception class for all exceptions thrown by the Searchable JPA framework.
 * This runtime exception serves as the parent class for more specific exceptions
 * that may occur during search operations, configuration, or validation.
 *
 * <p>Specific subclasses include:
 * <ul>
 *   <li>{@link SearchableConfigurationException} - For configuration-related errors</li>
 *   <li>{@link SearchableValidationException} - For search condition validation errors</li>
 *   <li>{@link SearchableOperationException} - For predicate/query execution errors</li>
 *   <li>{@link SearchableJoinException} - For join resolution errors</li>
 *   <li>{@link SearchableParseException} - For parameter/value parsing errors</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     searchableService.executeSearch(condition);
 * } catch (SearchableException e) {
 *     // Handle any searchable-related exception
 *     logger.error("Search operation failed", e);
 * }
 * }</pre>
 *
 * @see RuntimeException
 * @see SearchableConfigurationException
 */
public class SearchableException extends RuntimeException {
    /**
     * Constructs a new searchable exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the error
     */
    public SearchableException(String message) {
        super(message);
    }

    /**
     * Constructs a new searchable exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the error
     * @param cause   the underlying cause of the error
     */
    public SearchableException(String message, Throwable cause) {
        super(message, cause);
    }
}