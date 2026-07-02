package dev.simplecore.searchable.openapi;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.BETWEEN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.IN;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.NOT_IN;

/**
 * DTO fixtures for OpenAPI generator tests.
 */
public class OpenApiTestFixtures {

    public enum Status {
        ACTIVE, INACTIVE, PENDING
    }

    @Getter
    @Setter
    public static class EnumHolderDTO {
        @SearchableField(operators = {EQUALS, IN, NOT_IN, BETWEEN})
        private Status status;
    }

    @Getter
    @Setter
    public static class ArrayHolderDTO {
        @SearchableField(operators = {EQUALS, IN})
        private String[] tags;
    }

    @Getter
    @Setter
    public static class BetweenFirstDTO {
        @SearchableField(operators = {BETWEEN})
        private Long amount;
    }

    @Getter
    @Setter
    public static class BaseDocDTO {
        @SearchableField(entityField = "created_at", operators = {EQUALS}, sortable = true)
        private String inheritedField;
    }

    @Getter
    @Setter
    public static class ChildDocDTO extends BaseDocDTO {
        @SearchableField(operators = {EQUALS})
        private String ownField;
    }
}
