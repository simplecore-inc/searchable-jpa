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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:performance_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@DisplayName("Cursor Pagination Performance Test with Large Dataset")
class CursorPaginationLargeDataPerformanceTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private EntityManager entityManager;

    private static final int LARGE_DATASET_SIZE = 50_000; // Expanded to 50K records
    private static final int BATCH_SIZE = 1000;
    
    private List<TestAuthor> authors;

    @BeforeEach
    @Transactional
    void setUpLargeDataset() {
        // Clean up existing data
        entityManager.createQuery("DELETE FROM TestPost").executeUpdate();
        entityManager.createQuery("DELETE FROM TestAuthor").executeUpdate();
        entityManager.flush();
        
        System.out.println("=== Creating Large Dataset for Performance Testing ===");
        long startTime = System.currentTimeMillis();
        
        createAuthors();
        createLargePosts();
        
        long endTime = System.currentTimeMillis();
        System.out.printf("=== Dataset Creation Completed in %d ms ===\n", endTime - startTime);
    }

    private void createAuthors() {
        authors = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Performance Author " + i)
                    .email("perf" + i + "@test.com")
                    .nickname("perf" + i)
                    .build();
            entityManager.persist(author);
            authors.add(author);
        }
        entityManager.flush();
        System.out.println("Created 20 authors");
    }

    private void createLargePosts() {
        System.out.println("Creating " + LARGE_DATASET_SIZE + " posts in batches of " + BATCH_SIZE);
        
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime baseTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        
        // Title patterns for variety
        String[] prefixes = {"Fast", "Quick", "Rapid", "Swift", "Speedy", "Efficient", "Optimized", "Enhanced"};
        String[] suffixes = {"Post", "Article", "Content", "Item", "Entry", "Record", "Document", "Text"};
        
        for (int batch = 0; batch < LARGE_DATASET_SIZE / BATCH_SIZE; batch++) {
            for (int i = 0; i < BATCH_SIZE; i++) {
                int postIndex = batch * BATCH_SIZE + i;
                
                String prefix = prefixes[postIndex % prefixes.length];
                String suffix = suffixes[(postIndex / 1000) % suffixes.length];
                
                TestPost post = TestPost.builder()
                        .title(prefix + " " + suffix + " " + String.format("%06d", postIndex))
                        .content("Performance test content for post #" + postIndex)
                        .status(postIndex % 3 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED)
                        .viewCount((long) (random.nextInt(100000) + postIndex))
                        .likeCount((long) (random.nextInt(5000) + (postIndex % 500)))
                        .author(authors.get(postIndex % authors.size()))
                        .createdAt(baseTime.plusDays(postIndex / 100).plusHours(postIndex % 24))
                        .build();
                
                entityManager.persist(post);
            }
            
            // Flush every batch to manage memory
            entityManager.flush();
            entityManager.clear();
            
            // Re-attach authors after clear
            for (int i = 0; i < authors.size(); i++) {
                authors.set(i, entityManager.merge(authors.get(i)));
            }
            
            // Progress reporting
            if ((batch + 1) % 10 == 0) {
                System.out.printf("Processed %d/%d batches (%d posts)\n", 
                    batch + 1, LARGE_DATASET_SIZE / BATCH_SIZE, (batch + 1) * BATCH_SIZE);
            }
        }
        
        System.out.printf("Successfully created %d posts\n", LARGE_DATASET_SIZE);
    }

    @Test
    @DisplayName("First page should be extremely fast")
    void testFirstPagePerformance() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount"))
                .page(0)
                .size(20)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.printf("First page query time: %d ms\n", endTime - startTime);
        
        assertThat(endTime - startTime).as("First page should be very fast").isLessThan(150);
        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getTotalElements()).isGreaterThan(30000); // ~67% published
        
        // Verify sorting
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            assertThat(posts.get(i).getViewCount()).isGreaterThanOrEqualTo(posts.get(i + 1).getViewCount());
        }
    }

    @Test
    @DisplayName("Deep pagination performance should remain excellent")
    void testDeepPaginationPerformance() {
        // Test progressively deeper pages
        int[] deepPages = {10, 50, 100, 500, 1000, 2000}; // Up to 40,000th record
        
        for (int pageNum : deepPages) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(pageNum)
                    .size(20)
                    .build();

            long startTime = System.currentTimeMillis();
            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            long endTime = System.currentTimeMillis();

            System.out.printf("Page %d query time: %d ms (record ~%d)\n", 
                pageNum, endTime - startTime, pageNum * 20);
            
            // Even very deep pages should be fast with cursor-based pagination
            assertThat(endTime - startTime).as("Deep page " + pageNum + " should be fast").isLessThan(200);
            assertThat(result.getNumber()).isEqualTo(pageNum);
            
            if (!result.getContent().isEmpty()) {
                // Verify sorting is maintained
                List<TestPost> posts = result.getContent();
                for (int i = 0; i < posts.size() - 1; i++) {
                    assertThat(posts.get(i).getTitle()).isLessThanOrEqualTo(posts.get(i + 1).getTitle());
                }
            }
        }
    }

    @Test
    @DisplayName("Complex search performance should be efficient")
    void testComplexSearchPerformance() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED)
                           .and(c -> c.contains("searchTitle", "Fast Post"))
                           .and(c -> c.greaterThan("viewCount", 25000L)))
                .sort(s -> s.desc("viewCount").asc("createdAt"))
                .page(5)
                .size(30)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.printf("Complex search query time: %d ms\n", endTime - startTime);
        System.out.printf("Found %d matching records\n", result.getTotalElements());
        
        assertThat(endTime - startTime).as("Complex search should be efficient").isLessThan(150);
        
        // Verify search results
        result.getContent().forEach(post -> {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getTitle()).containsIgnoringCase("Fast Post");
            assertThat(post.getViewCount()).isGreaterThan(25000L);
        });
    }

    @Test
    @DisplayName("Large page sizes should work efficiently")
    void testLargePageSizes() {
        int[] pageSizes = {100, 500, 1000};
        
        for (int pageSize : pageSizes) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("createdAt"))
                    .page(0)
                    .size(pageSize)
                    .build();

            long startTime = System.currentTimeMillis();
            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            long endTime = System.currentTimeMillis();

            System.out.printf("Page size %d query time: %d ms\n", pageSize, endTime - startTime);
            
            assertThat(endTime - startTime).as("Large page size " + pageSize + " should be efficient").isLessThan(200);
            assertThat(result.getContent()).hasSizeLessThanOrEqualTo(pageSize);
            
            // Verify no duplicates
            Set<Long> ids = new HashSet<>();
            for (TestPost post : result.getContent()) {
                assertThat(ids.add(post.getId())).as("No duplicate IDs").isTrue();
            }
        }
    }

    @Test
    @DisplayName("Pagination consistency across multiple pages")
    void testPaginationConsistency() {
        int pageSize = 50;
        int totalPagesToTest = 20; // Test first 1000 records
        Set<Long> allSeenIds = new HashSet<>();
        
        for (int page = 0; page < totalPagesToTest; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            long startTime = System.currentTimeMillis();
            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            long endTime = System.currentTimeMillis();

            System.out.printf("Consistency test page %d time: %d ms\n", page, endTime - startTime);
            
            assertThat(endTime - startTime).as("Each page should be fast").isLessThan(100);
            
            // Check for duplicates across pages
            for (TestPost post : result.getContent()) {
                assertThat(allSeenIds.add(post.getId()))
                    .as("Post ID " + post.getId() + " should not appear in multiple pages")
                    .isTrue();
            }
            
            if (result.getContent().isEmpty()) {
                break;
            }
        }
        
        System.out.printf("Verified consistency across %d pages with %d unique records\n", 
            totalPagesToTest, allSeenIds.size());
    }

    @Test
    @DisplayName("Performance comparison: first vs deep pages")
    void testPerformanceComparison() {
        // Test first page
        SearchCondition<TestPostSearchDTO> firstPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(20)
                .build();

        long firstPageStart = System.currentTimeMillis();
        Page<TestPost> firstPageResult = testPostService.findAllWithSearch(firstPageCondition);
        long firstPageEnd = System.currentTimeMillis();
        long firstPageTime = firstPageEnd - firstPageStart;

        // Test very deep page
        SearchCondition<TestPostSearchDTO> deepPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(2000) // Very deep page
                .size(20)
                .build();

        long deepPageStart = System.currentTimeMillis();
        Page<TestPost> deepPageResult = testPostService.findAllWithSearch(deepPageCondition);
        long deepPageEnd = System.currentTimeMillis();
        long deepPageTime = deepPageEnd - deepPageStart;

        // Both should be fast, deep page shouldn't be significantly slower
        assertThat(firstPageTime).isLessThan(100);
        assertThat(deepPageTime).isLessThan(100);
        
        // With cursor-based pagination, deep page should not be more than 2x slower
        assertThat(deepPageTime).isLessThan(firstPageTime * 2);
        
        System.out.println("=== Performance Comparison ===");
        System.out.printf("First page time: %d ms\n", firstPageTime);
                 System.out.printf("Deep page (2000) time: %d ms\n", deepPageTime);
        System.out.printf("Performance ratio: %.2f\n", (double) deepPageTime / firstPageTime);
        
        // Verify both returned valid results
        assertThat(firstPageResult.getContent()).isNotEmpty();
        if (!deepPageResult.getContent().isEmpty()) {
            assertThat(deepPageResult.getContent()).hasSizeLessThanOrEqualTo(20);
        }
    }
} 