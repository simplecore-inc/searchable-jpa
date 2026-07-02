package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/**
 * Search DTO for {@link dev.simplecore.searchable.test.entity.TestOrderItem}. The embedded id fields
 * are mapped through the actual embedded attribute name ({@code orderItemId}).
 */
@Getter
@Setter
public class TestOrderItemSearchDTO {

    @SearchableField(entityField = "orderItemId.warehouseCode", operators = {EQUALS}, sortable = true)
    private String warehouseCode;

    @SearchableField(entityField = "orderItemId.lineNo", operators = {EQUALS}, sortable = true)
    private Long lineNo;

    @SearchableField(entityField = "name", operators = {EQUALS, CONTAINS}, sortable = true)
    private String name;

    @SearchableField(entityField = "notes.content", operators = {EQUALS, CONTAINS}, sortable = true)
    private String noteContent;
}
