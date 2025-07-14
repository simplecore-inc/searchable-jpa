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

        @SearchableField(entityField = "postId", operators = {EQUALS}, sortable = true)
        private Long id;
        
        @SearchableField(entityField = "postId", operators = {EQUALS}, sortable = true)
        private Long postId;

        @SearchableField(entityField = "author.authorId", operators = {EQUALS})
        private Long authorId;

        @SearchableField(entityField = "comments.commentId", operators = {EQUALS})
        private Long commentId;

        @Size(min = 2, message = "Title must be at least 2 characters")
        @Size(max = 100, message = "Title cannot exceed 100 characters")
        @SearchableField(entityField = "title", operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
        private String searchTitle;

        @SearchableField(operators = {EQUALS, NOT_EQUALS, IN, NOT_IN, IS_NULL, IS_NOT_NULL})
        private TestPostStatus status;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN, GREATER_THAN_OR_EQUAL_TO}, sortable = true)
        private Long viewCount;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
        private LocalDateTime createdAt;

        @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
        private LocalDateTime updatedAt;

        @Size(max = 50, message = "Author name cannot exceed 50 characters")
        @SearchableField(entityField = "author.name", operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
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

        @SearchableField(entityField = "tags.tagId", operators = {EQUALS, IN})
        private Long tagId;

        @Size(max = 50, message = "Tag name cannot exceed 50 characters")
        @SearchableField(entityField = "tags.name", operators = {EQUALS, CONTAINS, STARTS_WITH}, sortable = true)
        private String tagName;
    }

    @Data
    public static class PostUpdateDTO {
        private String title;

        private String content;

        private TestPostStatus status;
    }

    /**
     * DTO for testing sortField functionality
     */
    public static class TestPostSortFieldDTO {
        @SearchableField(operators = {EQUALS, CONTAINS})
        private String title;

        @SearchableField(operators = {EQUALS})
        private String status;

        @SearchableField(sortable = true, sortField = "author.name")
        private String authorName;

        @SearchableField(sortable = true, sortField = "createdAt")
        private String createdDate;

        @SearchableField(sortable = true, entityField = "updatedAt", sortField = "modifiedAt")
        private String lastModified;

        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }

        public String getCreatedDate() { return createdDate; }
        public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    }
}
