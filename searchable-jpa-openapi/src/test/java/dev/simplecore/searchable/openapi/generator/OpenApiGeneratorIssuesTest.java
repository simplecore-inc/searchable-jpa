package dev.simplecore.searchable.openapi.generator;

import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.openapi.OpenApiTestFixtures.ArrayHolderDTO;
import dev.simplecore.searchable.openapi.OpenApiTestFixtures.BetweenFirstDTO;
import dev.simplecore.searchable.openapi.OpenApiTestFixtures.ChildDocDTO;
import dev.simplecore.searchable.openapi.OpenApiTestFixtures.EnumHolderDTO;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Verifies Category 7 (OpenAPI module) issues 7-1 through 7-4.
 */
class OpenApiGeneratorIssuesTest {

    // ---- 7-1: enum values are retained for IN/NOT_IN/BETWEEN ----

    @Test
    @DisplayName("7-1: enum field with IN keeps the enum value list in the schema")
    void enumInSchemaKeepsEnumValues() throws Exception {
        Field statusField = EnumHolderDTO.class.getDeclaredField("status");
        ParameterSchemaGenerator generator = new ParameterSchemaGenerator();

        Schema<?> inSchema = generator.createFieldParameterSchema(statusField, SearchOperator.IN);

        assertThat(inSchema.getType()).isEqualTo("string");
        assertThat(enumValuesOf(inSchema)).containsExactlyInAnyOrder("ACTIVE", "INACTIVE", "PENDING");
    }

    private static List<String> enumValuesOf(Schema<?> schema) {
        return schema.getEnum().stream().map(String::valueOf).collect(Collectors.toList());
    }

    @Test
    @DisplayName("7-1: enum field with BETWEEN keeps the enum value list in the schema")
    void enumBetweenSchemaKeepsEnumValues() throws Exception {
        Field statusField = EnumHolderDTO.class.getDeclaredField("status");
        ParameterSchemaGenerator generator = new ParameterSchemaGenerator();

        Schema<?> betweenSchema = generator.createFieldParameterSchema(statusField, SearchOperator.BETWEEN);

        assertThat(enumValuesOf(betweenSchema)).containsExactlyInAnyOrder("ACTIVE", "INACTIVE", "PENDING");
    }

    // ---- 7-4: array field schema does not throw NPE ----

    @Test
    @DisplayName("7-4: array-typed field produces an array schema without a NullPointerException")
    void arrayFieldSchemaNoNpe() throws Exception {
        Field tagsField = ArrayHolderDTO.class.getDeclaredField("tags");
        ParameterSchemaGenerator generator = new ParameterSchemaGenerator();

        Schema<?>[] result = new Schema<?>[1];
        assertThatCode(() -> result[0] = generator.createFieldParameterSchema(tagsField, SearchOperator.EQUALS))
                .doesNotThrowAnyException();
        assertThat(result[0]).isNotNull();
        assertThat(result[0].getType()).isEqualTo("array");
    }

    // ---- 7-2: BETWEEN example uses distinct start/end values ----

    @Test
    @DisplayName("7-2: BETWEEN as the first operator produces distinct start/end example values")
    void betweenExampleHasDistinctBounds() {
        ExampleGenerator generator = new ExampleGenerator();

        String json = generator.generateSimpleExample(BetweenFirstDTO.class);

        // The between example must be [1, 100], not [1, 1].
        assertThat(json).contains("\"value\" : 1");
        assertThat(json).contains("\"value2\" : 100");
    }

    // ---- 7-3: inherited @SearchableField fields appear in the docs ----

    @Test
    @DisplayName("7-3: inherited @SearchableField fields appear as query parameters")
    void inheritedFieldAppearsInParameters() {
        Operation operation = new Operation();
        new ParameterGenerator().customizeParameters(operation, ChildDocDTO.class);

        List<String> parameterNames = operation.getParameters().stream()
                .map(Parameter::getName)
                .collect(Collectors.toList());

        assertThat(parameterNames).anyMatch(name -> name.startsWith("inheritedField."));
        assertThat(parameterNames).anyMatch(name -> name.startsWith("ownField."));
    }

    @Test
    @DisplayName("7-3: inherited @SearchableField fields appear in the operation description")
    void inheritedFieldAppearsInDescription() {
        String description = DescriptionGenerator.generateDescription(ChildDocDTO.class);

        assertThat(description).contains("inheritedField");
        assertThat(description).contains("ownField");
    }
}
