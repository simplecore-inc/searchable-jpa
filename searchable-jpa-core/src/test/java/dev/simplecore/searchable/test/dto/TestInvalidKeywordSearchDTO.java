package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/** Marks a field for the keyword search that cannot be matched by containment. */
@Getter
@Setter
public class TestInvalidKeywordSearchDTO {

    @SearchableField(entityField = "status", operators = {EQUALS}, keyword = true)
    private String status;
}
