package dev.simplecore.searchable.core.exception;

import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SearchableExceptionTest {

    @Test
    @DisplayName("SearchableException should be created with message")
    void testSearchableExceptionWithMessage() {
        // Given
        String message = "Test exception message";

        // When
        SearchableException exception = new SearchableException(message);

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }

    @Test
    @DisplayName("SearchableException should be created with message and cause")
    void testSearchableExceptionWithMessageAndCause() {
        // Given
        String message = "Test exception message";
        Throwable cause = new IllegalArgumentException("Original cause");

        // When
        SearchableException exception = new SearchableException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SearchableConfigurationException should be created properly")
    void testSearchableConfigurationException() {
        // Given
        String message = "Configuration error";

        // When
        SearchableConfigurationException exception = new SearchableConfigurationException(message);

        // Then
        assertThat(exception).isInstanceOf(SearchableException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("SearchableConfigurationException should be created with cause")
    void testSearchableConfigurationExceptionWithCause() {
        // Given
        String message = "Configuration error";
        Throwable cause = new ClassNotFoundException("Class not found");

        // When
        SearchableConfigurationException exception = new SearchableConfigurationException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SearchableValidationException should be created properly")
    void testSearchableValidationException() {
        // Given
        String message = "Validation error";

        // When
        SearchableValidationException exception = new SearchableValidationException(message);

        // Then
        assertThat(exception).isInstanceOf(javax.validation.ValidationException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("SearchableValidationException should be created with cause")
    void testSearchableValidationExceptionWithCause() {
        // Given
        String message = "Validation error";
        Throwable cause = new IllegalStateException("Invalid state");

        // When
        SearchableValidationException exception = new SearchableValidationException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SearchableOperationException should be created properly")
    void testSearchableOperationException() {
        // Given
        String message = "Operation failed";

        // When
        SearchableOperationException exception = new SearchableOperationException(message);

        // Then
        assertThat(exception).isInstanceOf(SearchableException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("SearchableOperationException should be created with cause")
    void testSearchableOperationExceptionWithCause() {
        // Given
        String message = "Operation failed";
        Throwable cause = new RuntimeException("Runtime error");

        // When
        SearchableOperationException exception = new SearchableOperationException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SearchableJoinException should be created properly")
    void testSearchableJoinException() {
        // Given
        String message = "Join operation failed";

        // When
        SearchableJoinException exception = new SearchableJoinException(message);

        // Then
        assertThat(exception).isInstanceOf(SearchableException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("SearchableJoinException should be created with cause")
    void testSearchableJoinExceptionWithCause() {
        // Given
        String message = "Join operation failed";
        Throwable cause = new IllegalArgumentException("Invalid join path");

        // When
        SearchableJoinException exception = new SearchableJoinException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("SearchableParseException should be created properly")
    void testSearchableParseException() {
        // Given
        String message = "Parse operation failed";

        // When
        SearchableParseException exception = new SearchableParseException(message);

        // Then
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    @DisplayName("SearchableParseException should be created with cause")
    void testSearchableParseExceptionWithCause() {
        // Given
        String message = "Parse operation failed";
        Throwable cause = new NumberFormatException("Invalid number format");

        // When
        SearchableParseException exception = new SearchableParseException(message, cause);

        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("Exception hierarchy should be correct")
    void testExceptionHierarchy() {
        // SearchableException hierarchy
        assertThat(SearchableException.class).isAssignableFrom(SearchableConfigurationException.class);
        assertThat(SearchableException.class).isAssignableFrom(SearchableOperationException.class);
        assertThat(SearchableException.class).isAssignableFrom(SearchableJoinException.class);
        
        // RuntimeException hierarchy
        assertThat(RuntimeException.class).isAssignableFrom(SearchableException.class);
        assertThat(RuntimeException.class).isAssignableFrom(SearchableParseException.class);
        
        // ValidationException hierarchy
        assertThat(javax.validation.ValidationException.class).isAssignableFrom(SearchableValidationException.class);
    }

    @Test
    @DisplayName("Exception messages should be preserved")
    void testExceptionMessagePreservation() {
        // Given
        String originalMessage = "Original error message";
        Throwable originalCause = new IllegalStateException("Original cause");
        
        // When creating chain of exceptions
        SearchableException baseException = new SearchableException(originalMessage, originalCause);
        SearchableConfigurationException configException = new SearchableConfigurationException("Config error", baseException);
        SearchableOperationException operationException = new SearchableOperationException("Operation error", configException);

        // Then
        assertThat(operationException.getMessage()).isEqualTo("Operation error");
        assertThat(operationException.getCause()).isEqualTo(configException);
        assertThat(operationException.getCause().getCause()).isEqualTo(baseException);
        assertThat(operationException.getCause().getCause().getCause()).isEqualTo(originalCause);
    }

    @Test
    @DisplayName("Null message should be handled properly")
    void testNullMessage() {
        // When
        SearchableException exception1 = new SearchableException(null);
        SearchableConfigurationException exception2 = new SearchableConfigurationException(null);
        SearchableValidationException exception3 = new SearchableValidationException(null);
        SearchableOperationException exception4 = new SearchableOperationException(null);
        SearchableJoinException exception5 = new SearchableJoinException(null);
        SearchableParseException exception6 = new SearchableParseException(null);

        // Then - should not throw exceptions
        assertThat(exception1.getMessage()).isNull();
        assertThat(exception2.getMessage()).isNull();
        assertThat(exception3.getMessage()).isNull();
        assertThat(exception4.getMessage()).isNull();
        assertThat(exception5.getMessage()).isNull();
        assertThat(exception6.getMessage()).isNull();
    }

    @Test
    @DisplayName("Empty message should be handled properly")
    void testEmptyMessage() {
        // When
        SearchableException exception1 = new SearchableException("");
        SearchableConfigurationException exception2 = new SearchableConfigurationException("");
        SearchableValidationException exception3 = new SearchableValidationException("");
        SearchableOperationException exception4 = new SearchableOperationException("");
        SearchableJoinException exception5 = new SearchableJoinException("");
        SearchableParseException exception6 = new SearchableParseException("");

        // Then
        assertThat(exception1.getMessage()).isEmpty();
        assertThat(exception2.getMessage()).isEmpty();
        assertThat(exception3.getMessage()).isEmpty();
        assertThat(exception4.getMessage()).isEmpty();
        assertThat(exception5.getMessage()).isEmpty();
        assertThat(exception6.getMessage()).isEmpty();
    }

    @Test
    @DisplayName("Exception stack trace should be preserved")
    void testStackTracePreservation() {
        // Given
        Throwable originalCause = new IllegalArgumentException("Original cause");
        
        // When
        SearchableException exception = new SearchableException("Test message", originalCause);
        
        // Then
        assertThat(exception.getStackTrace()).isNotEmpty();
        assertThat(exception.getCause().getStackTrace()).isNotEmpty();
        assertThat(exception.getCause().getStackTrace()).isEqualTo(originalCause.getStackTrace());
    }

    @Test
    @DisplayName("Exception should be serializable")
    void testExceptionSerialization() {
        // Given
        String message = "Test serialization";
        Throwable cause = new RuntimeException("Cause");
        
        // When
        SearchableException exception = new SearchableException(message, cause);
        
        // Then - should implement Serializable through RuntimeException
        assertThat(exception).isInstanceOf(java.io.Serializable.class);
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
} 