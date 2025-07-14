package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.annotation.SearchableField;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.service.TestPostService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static dev.simplecore.searchable.core.condition.operator.SearchOperator.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SortFieldFunctionalityTest {

    @Autowired
    private TestPostService testPostService;

    /**
     * DTO for testing sortField functionality
     */
    public static class TestSortFieldDTO {
        @SearchableField(operators = {EQUALS, CONTAINS})
        private String title;

        @SearchableField(sortable = true, sortField = "author.name")
        private String authorName;

        @SearchableField(sortable = true, sortField = "createdAt")
        private String createdDate;

        @SearchableField(sortable = true, entityField = "updatedAt", sortField = "author.email")
        private String lastModified;

        // Getters and setters
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getAuthorName() { return authorName; }
        public void setAuthorName(String authorName) { this.authorName = authorName; }

        public String getCreatedDate() { return createdDate; }
        public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }

        public String getLastModified() { return lastModified; }
        public void setLastModified(String lastModified) { this.lastModified = lastModified; }
    }

    @Test
    @DisplayName("Should sort by author.name when sortField is specified")
    void testSortBySortField_AuthorName() {
        // Given
        SearchCondition<TestSortFieldDTO> condition = SearchConditionBuilder.create(TestSortFieldDTO.class)
                .sort(sort -> sort.asc("authorName"))
                .page(0)
                .size(10)
                .build();

        // When
        Page<TestPost> result = testPostService.findAllWithSearch(condition);

        // Then - Test that query executes successfully
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(0);
        
        // If there are results, verify sorting by author name
        if (!result.getContent().isEmpty()) {
            for (int i = 0; i < result.getContent().size() - 1; i++) {
                TestPost current = result.getContent().get(i);
                TestPost next = result.getContent().get(i + 1);
                
                String currentAuthorName = current.getAuthor().getName();
                String nextAuthorName = next.getAuthor().getName();
                
                assertThat(currentAuthorName.compareTo(nextAuthorName)).isLessThanOrEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("Should sort by createdAt when sortField is specified")
    void testSortBySortField_CreatedAt() {
        // Given
        SearchCondition<TestSortFieldDTO> condition = SearchConditionBuilder.create(TestSortFieldDTO.class)
                .sort(sort -> sort.desc("createdDate"))
                .page(0)
                .size(10)
                .build();

        // When
        Page<TestPost> result = testPostService.findAllWithSearch(condition);

        // Then - Test that query executes successfully
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(0);
        
        // If there are results, verify sorting by created date in descending order
        if (!result.getContent().isEmpty()) {
            for (int i = 0; i < result.getContent().size() - 1; i++) {
                TestPost current = result.getContent().get(i);
                TestPost next = result.getContent().get(i + 1);
                
                assertThat(current.getCreatedAt()).isAfterOrEqualTo(next.getCreatedAt());
            }
        }
    }

    @Test
    @DisplayName("Should sort by sortField (author.email) over entityField (updatedAt)")
    void testSortField_PriorityOverEntityField() {
        // Given - lastModified has both entityField="updatedAt" and sortField="author.email"
        // sortField should take priority
        SearchCondition<TestSortFieldDTO> condition = SearchConditionBuilder.create(TestSortFieldDTO.class)
                .sort(sort -> sort.asc("lastModified"))
                .page(0)
                .size(10)
                .build();

        // When
        Page<TestPost> result = testPostService.findAllWithSearch(condition);

        // Then - Test that query executes successfully
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(0);
        
        // If there are results, verify sorting by author email (sortField takes priority over entityField)
        if (!result.getContent().isEmpty()) {
            for (int i = 0; i < result.getContent().size() - 1; i++) {
                TestPost current = result.getContent().get(i);
                TestPost next = result.getContent().get(i + 1);
                
                String currentAuthorEmail = current.getAuthor().getEmail();
                String nextAuthorEmail = next.getAuthor().getEmail();
                
                assertThat(currentAuthorEmail.compareTo(nextAuthorEmail)).isLessThanOrEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("Should handle multiple sort fields with sortField")
    void testMultipleSortFields() {
        // Given
        SearchCondition<TestSortFieldDTO> condition = SearchConditionBuilder.create(TestSortFieldDTO.class)
                .sort(sort -> sort
                        .asc("authorName")  // sorts by author.name
                        .desc("createdDate"))  // sorts by createdAt
                .page(0)
                .size(20)
                .build();

        // When
        Page<TestPost> result = testPostService.findAllWithSearch(condition);

        // Then - Test that query executes successfully
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(0);
        
        // If there are results, verify primary sort by author name, secondary sort by created date
        if (!result.getContent().isEmpty()) {
            for (int i = 0; i < result.getContent().size() - 1; i++) {
                TestPost current = result.getContent().get(i);
                TestPost next = result.getContent().get(i + 1);
                
                String currentAuthorName = current.getAuthor().getName();
                String nextAuthorName = next.getAuthor().getName();
                
                int nameComparison = currentAuthorName.compareTo(nextAuthorName);
                
                if (nameComparison == 0) {
                    // Same author name, verify secondary sort by created date (desc)
                    assertThat(current.getCreatedAt()).isAfterOrEqualTo(next.getCreatedAt());
                } else {
                    // Different author names, verify primary sort (asc)
                    assertThat(nameComparison).isLessThanOrEqualTo(0);
                }
            }
        }
    }

    @Test
    @DisplayName("Should work with search conditions and sortField")
    void testSearchWithSortField() {
        // Given
        SearchCondition<TestSortFieldDTO> condition = SearchConditionBuilder.create(TestSortFieldDTO.class)
                .where(where -> where
                        .contains("title", "Test"))
                .sort(sort -> sort.asc("authorName"))
                .page(0)
                .size(10)
                .build();

        // When
        Page<TestPost> result = testPostService.findAllWithSearch(condition);

        // Then - Test that query executes successfully
        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isGreaterThanOrEqualTo(0);
        
        // If there are results, verify search condition and sorting
        if (!result.getContent().isEmpty()) {
            // Verify all results contain "Test" in title
            result.getContent().forEach(post -> 
                assertThat(post.getTitle()).containsIgnoringCase("Test"));
            
            // Verify sorting by author name
            for (int i = 0; i < result.getContent().size() - 1; i++) {
                TestPost current = result.getContent().get(i);
                TestPost next = result.getContent().get(i + 1);
                
                String currentAuthorName = current.getAuthor().getName();
                String nextAuthorName = next.getAuthor().getName();
                
                assertThat(currentAuthorName.compareTo(nextAuthorName)).isLessThanOrEqualTo(0);
            }
        }
    }
} 