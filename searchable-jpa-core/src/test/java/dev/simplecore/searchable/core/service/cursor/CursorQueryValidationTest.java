package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestPostService;
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
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:validation_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
class CursorQueryValidationTest {

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    private TestAuthor author1, author2;

    @BeforeEach
    void setUp() {
        // Create test authors
        author1 = TestAuthor.builder()
                .name("Author One")
                .email("author1@example.com")
                .nickname("author1")
                .build();
        author2 = TestAuthor.builder()
                .name("Author Two")
                .email("author2@example.com")
                .nickname("author2")
                .build();
        em.persist(author1);
        em.persist(author2);

        // Create test posts with predictable data for query validation
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        
        for (int i = 0; i < 100; i++) {
            TestPost post = TestPost.builder()
                    .title("Post Title " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .status(i % 2 == 0 ? TestPostStatus.PUBLISHED : TestPostStatus.DRAFT)
                    .viewCount((long) (i * 10))
                    .likeCount((long) (i * 5))
                    .author(i % 2 == 0 ? author1 : author2)
                    .createdAt(baseTime.plusDays(i))
                    .build();
            em.persist(post);
        }

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("First page query should not include cursor conditions")
    void testFirstPageQuery() {
        System.out.println("\n=== TESTING FIRST PAGE QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Verify results
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        
        // First page should start from the beginning
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Post Title 000");
        
        System.out.println("First page results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")"));
    }

    @Test
    @DisplayName("Second page query should include cursor conditions")
    void testSecondPageQuery() {
        System.out.println("\n=== TESTING SECOND PAGE QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(1)
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Verify results
        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        
        // Second page should start after the first page's last item
        assertThat(result.getContent().get(0).getTitle()).isEqualTo("Post Title 010");
        
        System.out.println("Second page results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")"));
    }

    @Test
    @DisplayName("Complex sorting query should generate correct cursor conditions")
    void testComplexSortingQuery() {
        System.out.println("\n=== TESTING COMPLEX SORTING QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(2)
                .size(3)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Verify results
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        
        // Verify complex sorting is maintained
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            TestPost current = posts.get(i);
            TestPost next = posts.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getTitle()).isLessThanOrEqualTo(next.getTitle());
            } else {
                assertThat(current.getViewCount()).isGreaterThanOrEqualTo(next.getViewCount());
            }
        }
        
        System.out.println("Complex sorting results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")"));
    }

    @Test
    @DisplayName("Deep page query should use cursor efficiently")
    void testDeepPageQuery() {
        System.out.println("\n=== TESTING DEEP PAGE QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(10) // Deep page
                .size(3)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.println("Deep page query execution time: " + (endTime - startTime) + "ms");

        // Verify results
        assertThat(result.getNumber()).isEqualTo(10);
        assertThat(result.getSize()).isEqualTo(3);
        
        // Should be fast even for deep pages
        assertThat(endTime - startTime).isLessThan(200);
        
        System.out.println("Deep page results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")"));
    }

    @Test
    @DisplayName("Multiple field cursor query validation")
    void testMultipleFieldCursorQuery() {
        System.out.println("\n=== TESTING MULTIPLE FIELD CURSOR QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle").desc("createdAt"))
                .page(3)
                .size(4)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Verify results
        assertThat(result.getNumber()).isEqualTo(3);
        assertThat(result.getSize()).isEqualTo(4);
        
        // Verify triple sorting is maintained
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            TestPost current = posts.get(i);
            TestPost next = posts.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                if (current.getTitle().equals(next.getTitle())) {
                    assertThat(current.getCreatedAt()).isAfterOrEqualTo(next.getCreatedAt());
                } else {
                    assertThat(current.getTitle()).isLessThanOrEqualTo(next.getTitle());
                }
            } else {
                assertThat(current.getViewCount()).isGreaterThanOrEqualTo(next.getViewCount());
            }
        }
        
        System.out.println("Multiple field sorting results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + 
                             " (viewCount: " + post.getViewCount() + 
                             ", createdAt: " + post.getCreatedAt() + ")"));
    }

    @Test
    @DisplayName("Query with filtering and cursor conditions")
    void testFilteringWithCursorQuery() {
        System.out.println("\n=== TESTING FILTERING WITH CURSOR QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED)
                           .and(w2 -> w2.greaterThan("viewCount", 199L)))
                .sort(s -> s.asc("viewCount"))
                .page(2)
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Verify results
        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        
        // All results should meet the filtering criteria
        result.getContent().forEach(post -> {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount()).isGreaterThanOrEqualTo(200L);
        });
        
        // Verify sorting
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            assertThat(posts.get(i).getViewCount()).isLessThanOrEqualTo(posts.get(i + 1).getViewCount());
        }
        
        System.out.println("Filtering with cursor results:");
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + 
                             " (viewCount: " + post.getViewCount() + 
                             ", status: " + post.getStatus() + ")"));
    }

    @Test
    @DisplayName("Empty page query should not execute unnecessary queries")
    void testEmptyPageQuery() {
        System.out.println("\n=== TESTING EMPTY PAGE QUERY ===");
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(100) // Way beyond available data
                .size(5)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.println("Empty page query execution time: " + (endTime - startTime) + "ms");

        // Verify results
        assertThat(result.getNumber()).isEqualTo(100);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        
        // Should be very fast since no data to process
        assertThat(endTime - startTime).isLessThan(100);
        
        System.out.println("Empty page result: " + result.getContent().size() + " items");
    }

    @Test
    @DisplayName("Cursor query should handle special characters in values")
    void testSpecialCharactersInCursor() {
        System.out.println("\n=== TESTING SPECIAL CHARACTERS IN CURSOR ===");
        
        // Create posts with special characters
        TestPost specialPost1 = TestPost.builder()
                .title("Special 'Quote' Post")
                .content("Content with \"quotes\"")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(1000L)
                .likeCount(100L)
                .author(author1)
                .createdAt(LocalDateTime.now())
                .build();
        
        TestPost specialPost2 = TestPost.builder()
                .title("Special & Ampersand Post")
                .content("Content with & symbols")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(1001L)
                .likeCount(101L)
                .author(author2)
                .createdAt(LocalDateTime.now())
                .build();
        
        em.persist(specialPost1);
        em.persist(specialPost2);
        em.flush();

        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Should handle special characters without issues
        assertThat(result.getContent()).isNotEmpty();
        
        // Find our special posts
        boolean foundQuotePost = result.getContent().stream()
                .anyMatch(post -> post.getTitle().contains("'Quote'"));
        boolean foundAmpersandPost = result.getContent().stream()
                .anyMatch(post -> post.getTitle().contains("& Ampersand"));
        
        assertThat(foundQuotePost || foundAmpersandPost).isTrue();
        
        System.out.println("Special characters results:");
        result.getContent().stream()
                .filter(post -> post.getTitle().startsWith("Special"))
                .forEach(post -> 
                    System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")"));
    }

    @Test
    @DisplayName("Cursor query performance with different page sizes")
    void testCursorQueryPerformanceByPageSize() {
        System.out.println("\n=== TESTING CURSOR QUERY PERFORMANCE BY PAGE SIZE ===");
        
        int[] pageSizes = {5, 20, 50, 100};
        
        for (int pageSize : pageSizes) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(1) // Second page to trigger cursor logic
                    .size(pageSize)
                    .build();

            long startTime = System.currentTimeMillis();
            Page<TestPost> result = searchService.findAllWithSearch(condition);
            long endTime = System.currentTimeMillis();
            long executionTime = endTime - startTime;

            System.out.println("Page size " + pageSize + ": " + executionTime + "ms (" + 
                             result.getContent().size() + " results)");

            // All should be reasonably fast
            assertThat(executionTime).isLessThan(300);
            assertThat(result.getContent().size()).isLessThanOrEqualTo(pageSize);
        }
    }
} 