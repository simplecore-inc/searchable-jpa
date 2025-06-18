package dev.simplecore.searchable.core.exception;

import dev.simplecore.searchable.core.annotation.SearchableField;

/**
 * Exception thrown when there is a configuration error in the Searchable JPA setup.
 * This exception is typically thrown when:
 * <ul>
 *   <li>Invalid or missing {@code @SearchableField} annotations</li>
 *   <li>Incorrect entity mapping configurations</li>
 *   <li>Invalid search field configurations</li>
 *   <li>Incompatible field type mappings</li>
 * </ul>
 *
 * <p>Example scenarios:
 * <pre>{@code
 * // Invalid field type mapping
 * @SearchableField
 * private LocalDateTime stringField; // Throws exception if mapped to String
 *
 * // Missing required configuration
 * @SearchableField(operators = {})
 * private String field; // Throws exception if no operators specified
 * }</pre>
 *
 * @see SearchableField
 */
public class SearchableConfigurationException extends SearchableException {
    /**
     * Constructs a new configuration exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the configuration error
     */
    public SearchableConfigurationException(String message) {
        super(message);
    }

    /**
     * Constructs a new configuration exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the configuration error
     * @param cause   the underlying cause of the configuration error
     */
    public SearchableConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}