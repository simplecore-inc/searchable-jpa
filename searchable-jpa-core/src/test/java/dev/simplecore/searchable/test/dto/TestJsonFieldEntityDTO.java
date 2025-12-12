package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;

@Getter
@Setter
public class TestJsonFieldEntityDTO {

    @SearchableField(operators = {EQUALS}, sortable = true)
    private Long id;

    @SearchableField(operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
    private String name;

    @SearchableField(operators = {CONTAINS, STARTS_WITH, ENDS_WITH, NOT_CONTAINS})
    private Map<String, String> descriptionI18n;

    @SearchableField(operators = {CONTAINS, STARTS_WITH, ENDS_WITH})
    private Map<String, String> metadata;
}