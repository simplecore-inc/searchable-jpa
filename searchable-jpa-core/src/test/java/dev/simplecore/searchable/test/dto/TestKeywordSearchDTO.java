package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/** Search DTO over {@code TestUpdatableEntity} whose name and status take part in the keyword search. */
@Getter
@Setter
public class TestKeywordSearchDTO {

    @SearchableField(entityField = "id", operators = {EQUALS}, sortable = true)
    private Long id;

    @SearchableField(entityField = "name", operators = {EQUALS, CONTAINS}, sortable = true, keyword = true)
    private String name;

    @SearchableField(entityField = "status", operators = {EQUALS, CONTAINS}, keyword = true)
    private String status;

    @SearchableField(entityField = "quantity", operators = {EQUALS})
    private Integer quantity;
}
