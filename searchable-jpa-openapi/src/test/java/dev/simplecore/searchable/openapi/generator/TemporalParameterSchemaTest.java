package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that temporal search parameters emit the OpenAPI formats the frontend codegen
 * uses to pick date/date-time/time pickers: Instant/OffsetDateTime/ZonedDateTime emit
 * format:date-time, LocalDate emits format:date, and LocalTime emits format:partial-time.
 */
class TemporalParameterSchemaTest {

    static class TemporalHolderDTO {
        @SearchableField(operators = {EQUALS})
        private Instant eventAt;

        @SearchableField(operators = {EQUALS})
        private LocalDate holidayDate;

        @SearchableField(operators = {EQUALS})
        private LocalTime shiftStart;
    }

    private static Schema<?> schemaFor(String fieldName) throws Exception {
        Field field = TemporalHolderDTO.class.getDeclaredField(fieldName);
        return new ParameterSchemaGenerator().createFieldParameterSchema(field, SearchOperator.EQUALS);
    }

    @Test
    @DisplayName("Instant search parameter emits format:date-time")
    void instantParameterEmitsDateTimeFormat() throws Exception {
        Schema<?> schema = schemaFor("eventAt");

        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isEqualTo("date-time");
    }

    @Test
    @DisplayName("LocalDate search parameter emits format:date")
    void localDateParameterEmitsDateFormat() throws Exception {
        Schema<?> schema = schemaFor("holidayDate");

        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isEqualTo("date");
    }

    @Test
    @DisplayName("LocalTime search parameter emits format:partial-time")
    void localTimeParameterEmitsPartialTimeFormat() throws Exception {
        Schema<?> schema = schemaFor("shiftStart");

        assertThat(schema.getType()).isEqualTo("string");
        assertThat(schema.getFormat()).isEqualTo("partial-time");
        assertThat(schema.getDescription()).contains("HH:mm");
    }
}
