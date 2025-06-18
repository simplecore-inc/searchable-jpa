package dev.simplecore.searchable.test.dto;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.time.LocalDateTime;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;

public class TestPostDTOs {

    @Getter
    @Setter
    public static class TestPostSearchDTO {

        @SearchableField(entityField = "postId", operators = {EQUALS})
        private Long id;

        @SearchableField(entityField = "author.authorId", operators = {EQUALS})
        private Long authorId;

        @SearchableField(entityField = "comments.commentId", operators = {EQUALS})
        private Long commentId;

        @Size(min = 5, message = "Title must be at least 5 characters")
        @Size(max = 100, message = "Title cannot exceed 100 characters")
        @SearchableField(entityField = "title", operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
        private String searchTitle;

        @SearchableField(operators = {EQUALS, NOT_EQUALS, IN, NOT_IN, IS_NULL, IS_NOT_NULL})
        private TestPostStatus status;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
        private Long viewCount;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
        private LocalDateTime createdAt;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
        private LocalDateTime updatedAt;

        @Size(max = 50, message = "Author name cannot exceed 50 characters")
        @SearchableField(entityField = "author.name", operators = {EQUALS, CONTAINS, STARTS_WITH})
        private String authorName;

        @Size(max = 100, message = "Email address cannot exceed 100 characters")
        @SearchableField(entityField = "author.email", operators = {EQUALS, CONTAINS, ENDS_WITH})
        private String authorEmail;

        @Size(max = 500, message = "Comment content cannot exceed 500 characters")
        @SearchableField(entityField = "comments.content", operators = {CONTAINS})
        private String commentContent;

        @Size(max = 50, message = "Comment author name cannot exceed 50 characters")
        @SearchableField(entityField = "comments.author.name", operators = {EQUALS, CONTAINS})
        private String commentAuthorName;
    }

    @Data
    public static class PostUpdateDTO {
        private String title;

        private String content;

        private TestPostStatus status;
    }
}
