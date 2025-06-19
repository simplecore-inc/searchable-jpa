package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestPostService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Comprehensive test for cursor pagination boundary conditions and edge cases.
 * Tests various scenarios that could cause issues in production environments.
 */
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cursor_boundary_conditions_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.dev.simplecore.searchable=DEBUG"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@Slf4j
class CursorPaginationBoundaryConditionsTest {

    @Autowired
    private TestPostService testPostService;

    @PersistenceContext
    private EntityManager entityManager;

    private List<TestAuthor> testAuthors;

    @BeforeEach
    void setUp() {
        cleanupExistingData();
        createTestAuthors();
    }

    private void cleanupExistingData() {
        entityManager.createQuery("DELETE FROM TestPost").executeUpdate();
        entityManager.createQuery("DELETE FROM TestAuthor").executeUpdate();
        entityManager.flush();
    }

    private void createTestAuthors() {
        testAuthors = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + i)
                    .email("author" + i + "@test.com")
                    .nickname("author" + i)
                    .build();
            entityManager.persist(author);
            testAuthors.add(author);
        }
        entityManager.flush();
    }

    @Test
    @DisplayName("Empty dataset should return empty results gracefully")
    void testEmptyDataset() {
        // No data created - test with empty database
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getNumber()).isZero();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
        
        log.info("Empty dataset test passed - returned empty page correctly");
    }

    @Test
    @DisplayName("Single record dataset should handle pagination correctly")
    void testSingleRecordDataset() {
        // Create only one record
        TestPost singlePost = TestPost.builder()
                .title("Single Post")
                .content("Only content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(100L)
                .likeCount(10L)
                .author(testAuthors.get(0))
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(singlePost);
        entityManager.flush();
        
        // Test first page
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Single Post");
        
        // Test second page (should be empty)
        SearchCondition<TestPostSearchDTO> secondPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(1)
                .size(10)
                .build();

        Page<TestPost> secondPageResult = testPostService.findAllWithSearch(secondPageCondition);
        
        assertThat(secondPageResult.getContent()).isEmpty();
        assertThat(secondPageResult.hasNext()).isFalse();
        
        log.info("Single record dataset test passed");
    }

    @Test
    @DisplayName("Identical sort values should maintain consistent order")
    void testIdenticalSortValues() {
        LocalDateTime sameTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        Long sameViewCount = 1000L;
        
        // Create multiple records with identical sort values
        for (int i = 0; i < 20; i++) {
            TestPost post = TestPost.builder()
                    .title("Post " + String.format("%02d", i))
                    .content("Content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount(sameViewCount) // Same view count for all
                    .likeCount((long) i)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(sameTime) // Same creation time for all
                    .build();
            entityManager.persist(post);
        }
        entityManager.flush();
        
        // Test pagination with identical sort values
        List<TestPost> allResults = new ArrayList<>();
        int pageSize = 5;
        int page = 0;
        
        while (true) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("viewCount").desc("createdAt")) // Both fields have identical values
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            if (result.getContent().isEmpty()) {
                break;
            }
            
            allResults.addAll(result.getContent());
            page++;
            
            if (page > 10) { // Safety break
                break;
            }
        }
        
        // Verify all records were retrieved
        assertThat(allResults).hasSize(20);
        
        // Verify no duplicates
        List<Long> ids = allResults.stream().map(TestPost::getPostId).toList();
        assertThat(ids).doesNotHaveDuplicates();
        
        log.info("Identical sort values test passed - {} records retrieved without duplicates", allResults.size());
    }

    @Test
    @DisplayName("Extreme numeric values should be handled correctly")
    void testExtremeNumericValues() {
        // Create records with extreme values
        TestPost minValuePost = TestPost.builder()
                .title("Min Value Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(0L) // Minimum
                .likeCount(0L)
                .author(testAuthors.get(0))
                .createdAt(LocalDateTime.of(1970, 1, 1, 0, 0)) // Very old date
                .build();
        entityManager.persist(minValuePost);
        
        TestPost maxValuePost = TestPost.builder()
                .title("Max Value Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(Long.MAX_VALUE) // Maximum
                .likeCount(Long.MAX_VALUE)
                .author(testAuthors.get(1))
                .createdAt(LocalDateTime.of(2099, 12, 31, 23, 59)) // Future date
                .build();
        entityManager.persist(maxValuePost);
        
        TestPost normalPost = TestPost.builder()
                .title("Normal Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(1000L)
                .likeCount(100L)
                .author(testAuthors.get(2))
                .createdAt(LocalDateTime.now())
                .build();
        entityManager.persist(normalPost);
        
        entityManager.flush();
        
        // Test sorting by view count (ascending)
        SearchCondition<TestPostSearchDTO> ascCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> ascResult = testPostService.findAllWithSearch(ascCondition);
        
        assertThat(ascResult.getContent()).hasSize(3);
        assertThat(ascResult.getContent().get(0).getTitle()).isEqualTo("Min Value Post");
        assertThat(ascResult.getContent().get(1).getTitle()).isEqualTo("Normal Post");
        assertThat(ascResult.getContent().get(2).getTitle()).isEqualTo("Max Value Post");
        
        // Test sorting by view count (descending)
        SearchCondition<TestPostSearchDTO> descCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> descResult = testPostService.findAllWithSearch(descCondition);
        
        assertThat(descResult.getContent()).hasSize(3);
        assertThat(descResult.getContent().get(0).getTitle()).isEqualTo("Max Value Post");
        assertThat(descResult.getContent().get(1).getTitle()).isEqualTo("Normal Post");
        assertThat(descResult.getContent().get(2).getTitle()).isEqualTo("Min Value Post");
        
        log.info("Extreme numeric values test passed");
    }

    @Test
    @DisplayName("Special characters in text fields should be handled correctly")
    void testSpecialCharactersInText() {
        String[] specialTitles = {
            "Post with émojis 🚀💯",
            "Post with 'quotes' and \"double quotes\"",
            "Post with <HTML> & XML tags",
            "Post with unicode: 한글 中文 العربية",
            "Post with symbols: @#$%^&*()_+-=[]{}|;':\",./<>?",
            "Post with newlines\nand\ttabs",
            "Post with NULL\0character",
            "Post with very long title: " + "A".repeat(200)
        };
        
        for (int i = 0; i < specialTitles.length; i++) {
            TestPost post = TestPost.builder()
                    .title(specialTitles[i])
                    .content("Content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) i * 100)
                    .likeCount((long) i * 10)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(LocalDateTime.now().plusMinutes(i))
                    .build();
            entityManager.persist(post);
        }
        entityManager.flush();
        
        // Test sorting by title
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(20)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        assertThat(result.getContent()).hasSize(specialTitles.length);
        
        // Verify all special characters are preserved
        List<String> retrievedTitles = result.getContent().stream()
                .map(TestPost::getTitle)
                .toList();
        
        for (String originalTitle : specialTitles) {
            assertThat(retrievedTitles).contains(originalTitle);
        }
        
        log.info("Special characters test passed - all {} titles preserved correctly", specialTitles.length);
    }

    @Test
    @DisplayName("Large page sizes should be handled appropriately")
    void testLargePageSizes() {
        // Create 100 records
        for (int i = 0; i < 100; i++) {
            TestPost post = TestPost.builder()
                    .title("Post " + String.format("%03d", i))
                    .content("Content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) i)
                    .likeCount((long) i / 2)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(LocalDateTime.now().plusMinutes(i))
                    .build();
            entityManager.persist(post);
            
            if (i % 20 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        entityManager.flush();
        
        // Test with very large page size
        SearchCondition<TestPostSearchDTO> largePageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(1000) // Larger than total records
                .build();

        Page<TestPost> largePageResult = testPostService.findAllWithSearch(largePageCondition);
        
        assertThat(largePageResult.getContent()).hasSize(100); // Should return all available records
        assertThat(largePageResult.getTotalElements()).isEqualTo(100);
        assertThat(largePageResult.hasNext()).isFalse();
        
        // Test with page size equal to total records
        SearchCondition<TestPostSearchDTO> exactPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(100)
                .build();

        Page<TestPost> exactPageResult = testPostService.findAllWithSearch(exactPageCondition);
        
        assertThat(exactPageResult.getContent()).hasSize(100);
        assertThat(exactPageResult.hasNext()).isFalse();
        
        log.info("Large page sizes test passed");
    }

    @Test
    @DisplayName("Zero and negative page numbers should be handled correctly")
    void testInvalidPageNumbers() {
        // Create some test data
        for (int i = 0; i < 10; i++) {
            TestPost post = TestPost.builder()
                    .title("Post " + i)
                    .content("Content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) i * 100)
                    .likeCount((long) i * 10)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(LocalDateTime.now().plusMinutes(i))
                    .build();
            entityManager.persist(post);
        }
        entityManager.flush();
        
        // Test page 0 (should work normally)
        SearchCondition<TestPostSearchDTO> page0Condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(5)
                .build();

        Page<TestPost> page0Result = testPostService.findAllWithSearch(page0Condition);
        
        assertThat(page0Result.getContent()).hasSize(5);
        assertThat(page0Result.getNumber()).isZero();
        
        // Test very high page number (beyond available data)
        SearchCondition<TestPostSearchDTO> highPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount"))
                .page(1000)
                .size(5)
                .build();

        Page<TestPost> highPageResult = testPostService.findAllWithSearch(highPageCondition);
        
        assertThat(highPageResult.getContent()).isEmpty();
        assertThat(highPageResult.getNumber()).isEqualTo(1000);
        assertThat(highPageResult.hasNext()).isFalse();
        
        log.info("Invalid page numbers test passed");
    }

    @Test
    @DisplayName("Complex filtering with boundary conditions")
    void testComplexFilteringBoundaryConditions() {
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        
        // Create records with various boundary values
        for (int i = 0; i < 50; i++) {
            TestPost post = TestPost.builder()
                    .title("Boundary Post " + i)
                    .content("Content " + i)
                    .status(i % 2 == 0 ? TestPostStatus.PUBLISHED : TestPostStatus.DRAFT)
                    .viewCount((long) i * 100)
                    .likeCount((long) i * 10)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(baseTime.plusDays(i))
                    .build();
            entityManager.persist(post);
        }
        entityManager.flush();
        
        // Test filtering at exact boundary values
        SearchCondition<TestPostSearchDTO> boundaryCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .greaterThan("viewCount", 900L) // Just below boundary
                        .lessThan("viewCount", 3100L)    // Just above boundary
                )
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(20)
                .build();

        Page<TestPost> boundaryResult = testPostService.findAllWithSearch(boundaryCondition);
        
        // Should include records with viewCount 1000, 1200, 1400, ..., 3000 (only published ones)
        assertThat(boundaryResult.getContent()).isNotEmpty();
        
        for (TestPost post : boundaryResult.getContent()) {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount()).isBetween(1000L, 3000L);
        }
        
        // Test with no matching results
        SearchCondition<TestPostSearchDTO> noMatchCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .greaterThan("viewCount", 10000L) // Beyond available data
                )
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(20)
                .build();

        Page<TestPost> noMatchResult = testPostService.findAllWithSearch(noMatchCondition);
        
        assertThat(noMatchResult.getContent()).isEmpty();
        assertThat(noMatchResult.getTotalElements()).isZero();
        
        log.info("Complex filtering boundary conditions test passed");
    }

    @Test
    @DisplayName("Mixed data types in sorting should work correctly")
    void testMixedDataTypesSorting() {
        LocalDateTime now = LocalDateTime.now();
        
        // Create records with various data type combinations
        TestPost post1 = TestPost.builder()
                .title("A First Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(100L)
                .likeCount(50L)
                .author(testAuthors.get(0))
                .createdAt(now.minusDays(1))
                .build();
        
        TestPost post2 = TestPost.builder()
                .title("B Second Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(100L) // Same as post1
                .likeCount(25L)  // Different
                .author(testAuthors.get(1))
                .createdAt(now.minusDays(1)) // Same as post1
                .build();
        
        TestPost post3 = TestPost.builder()
                .title("C Third Post")
                .content("Content")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(200L)
                .likeCount(75L)
                .author(testAuthors.get(2))
                .createdAt(now)
                .build();
        
        entityManager.persist(post1);
        entityManager.persist(post2);
        entityManager.persist(post3);
        entityManager.flush();
        
        // Test multi-field sorting with mixed data types
        SearchCondition<TestPostSearchDTO> mixedSortCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s
                        .asc("viewCount")      // Long
                        .desc("createdAt")     // LocalDateTime
                        .asc("searchTitle")    // String
                )
                .page(0)
                .size(10)
                .build();

        Page<TestPost> mixedSortResult = testPostService.findAllWithSearch(mixedSortCondition);
        
        assertThat(mixedSortResult.getContent()).hasSize(3);
        
        // Verify correct sort order
        List<TestPost> sortedPosts = mixedSortResult.getContent();
        assertThat(sortedPosts.get(0).getTitle()).isEqualTo("A First Post");  // viewCount=100, older date, title starts with A
        assertThat(sortedPosts.get(1).getTitle()).isEqualTo("B Second Post"); // viewCount=100, older date, title starts with B
        assertThat(sortedPosts.get(2).getTitle()).isEqualTo("C Third Post");  // viewCount=200
        
        log.info("Mixed data types sorting test passed");
    }
} 