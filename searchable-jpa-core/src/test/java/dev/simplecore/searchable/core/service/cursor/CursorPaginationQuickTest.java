package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestPostService;
import javax.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@Transactional
class CursorPaginationQuickTest {

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    private static final int TOTAL_POSTS = 100; // Small dataset for quick tests
    private static final int TOTAL_AUTHORS = 5;
    private List<TestAuthor> authors;

    @BeforeEach
    void setUp() {
        createTestDataset();
    }

    private void createTestDataset() {
        // Create authors
        authors = new ArrayList<>();
        for (int i = 0; i < TOTAL_AUTHORS; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + i)
                    .email("author" + i + "@example.com")
                    .nickname("author" + i)
                    .build();
            authors.add(author);
            em.persist(author);
        }

        // Create posts
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        for (int i = 0; i < TOTAL_POSTS; i++) {
            TestPost post = TestPost.builder()
                    .title("Test Post " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .status(i % 2 == 0 ? TestPostStatus.PUBLISHED : TestPostStatus.DRAFT)
                    .viewCount((long) (i * 10))
                    .likeCount((long) (i * 5))
                    .author(authors.get(i % TOTAL_AUTHORS))
                    .createdAt(baseTime.plusDays(i))
                    .build();
            em.persist(post);
        }
        em.flush();
    }

    @Test
    @DisplayName("Quick cursor pagination performance test")
    void testQuickCursorPagination() {
        // Test first page
        SearchCondition<TestPostSearchDTO> condition1 = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(10)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> page1 = searchService.findAllWithSearch(condition1);
        long endTime = System.currentTimeMillis();

        System.out.println("=== FIRST PAGE PERFORMANCE ===");
        System.out.println("Time: " + (endTime - startTime) + "ms");
        System.out.println("Results: " + page1.getContent().size());
        
        assertThat(endTime - startTime).isLessThan(1000); // Should be very fast
        assertThat(page1.getContent()).isNotEmpty();
        assertThat(page1.getNumber()).isEqualTo(0);

        // Test deep page
        SearchCondition<TestPostSearchDTO> condition2 = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(3) // Deep enough for cursor to kick in
                .size(10)
                .build();

        startTime = System.currentTimeMillis();
        Page<TestPost> page2 = searchService.findAllWithSearch(condition2);
        endTime = System.currentTimeMillis();

        System.out.println("=== DEEP PAGE PERFORMANCE ===");
        System.out.println("Time: " + (endTime - startTime) + "ms");
        System.out.println("Results: " + page2.getContent().size());
        
        assertThat(endTime - startTime).isLessThan(1000); // Should still be fast
        assertThat(page2.getNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("Complex sorting cursor performance")
    void testComplexSortingPerformance() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(2)
                .size(5)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.println("=== COMPLEX SORTING PERFORMANCE ===");
        System.out.println("Time: " + (endTime - startTime) + "ms");
        System.out.println("Results: " + result.getContent().size());
        
        assertThat(endTime - startTime).isLessThan(1000);
        assertThat(result.getNumber()).isEqualTo(2);
        
        // Verify sorting
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
    }

    @Test
    @DisplayName("SQL query generation verification")
    void testQueryGeneration() {
        System.out.println("=== TESTING SQL QUERY GENERATION ===");
        
        // This test is mainly for observing the generated SQL in logs
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(1)
                .size(3)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);
        
        assertThat(result).isNotNull();
        assertThat(result.getNumber()).isEqualTo(1);
        
        System.out.println("Page number: " + result.getNumber());
        System.out.println("Page size: " + result.getSize());
        System.out.println("Total elements: " + result.getTotalElements());
        System.out.println("Total pages: " + result.getTotalPages());
        System.out.println("Has next: " + result.hasNext());
        System.out.println("Has previous: " + result.hasPrevious());
        
        result.getContent().forEach(post -> 
            System.out.println("  - " + post.getTitle() + " (viewCount: " + post.getViewCount() + ")")
        );
    }
} 