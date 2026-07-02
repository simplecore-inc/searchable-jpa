package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

@Getter
@Setter
public class TestUpdatableSearchDTO {

    @SearchableField(entityField = "id", operators = {EQUALS}, sortable = true)
    private Long id;

    @SearchableField(entityField = "status", operators = {EQUALS})
    private String status;

    @SearchableField(entityField = "name", operators = {EQUALS, CONTAINS}, sortable = true)
    private String name;
}
