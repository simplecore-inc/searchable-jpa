package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import lombok.Getter;
import lombok.Setter;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.EQUALS;

/**
 * Search DTO exposing a to-many field ({@code comments.content}) as sortable, used to verify that
 * sorting a single primary-key entity through a to-many relationship keeps pagination stable (1-4).
 */
@Getter
@Setter
public class TestPostToManySortDTO {

    @SearchableField(entityField = "postId", operators = {EQUALS}, sortable = true)
    private Long postId;

    @SearchableField(operators = {EQUALS})
    private TestPostStatus status;

    @SearchableField(entityField = "comments.content", operators = {EQUALS, CONTAINS}, sortable = true)
    private String commentContent;
}
