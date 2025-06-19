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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.*;

/**
 * Performance benchmark test for cursor-based pagination vs traditional offset-based pagination.
 * Tests performance characteristics across different data sizes and query patterns.
 */
@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:cursor_performance_benchmark_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "logging.level.dev.simplecore.searchable=DEBUG",
    "spring.jpa.show-sql=false", // Disable SQL logging for performance testing
    "logging.level.org.hibernate.SQL=WARN",
    "logging.level.org.hibernate.type.descriptor.sql.BasicBinder=WARN"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@Slf4j
class CursorPaginationPerformanceBenchmarkTest {

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
        for (int i = 1; i <= 10; i++) {
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
    @DisplayName("Performance comparison: Small dataset (1K records)")
    void testSmallDatasetPerformance() {
        int dataSize = 1000;
        createTestDataset(dataSize);
        
        PerformanceResult cursorResult = measureCursorPaginationPerformance(dataSize, 20, 5);
        
        log.info("=== Small Dataset Performance (1K records) ===");
        log.info("Cursor Pagination - Total: {}ms, Avg per page: {}ms", 
                cursorResult.totalTime, cursorResult.averageTime);
        
        // Performance assertions
        assertThat(cursorResult.totalTime).as("Small dataset should be fast").isLessThan(2000);
        assertThat(cursorResult.averageTime).as("Average page time should be reasonable").isLessThan(500);
        assertThat(cursorResult.dataIntegrityValid).as("Data integrity should be maintained").isTrue();
    }

    @Test
    @DisplayName("Performance comparison: Medium dataset (10K records)")
    void testMediumDatasetPerformance() {
        int dataSize = 10000;
        createTestDataset(dataSize);
        
        PerformanceResult cursorResult = measureCursorPaginationPerformance(dataSize, 50, 10);
        
        log.info("=== Medium Dataset Performance (10K records) ===");
        log.info("Cursor Pagination - Total: {}ms, Avg per page: {}ms", 
                cursorResult.totalTime, cursorResult.averageTime);
        
        // Performance assertions
        assertThat(cursorResult.totalTime).as("Medium dataset should scale well").isLessThan(5000);
        assertThat(cursorResult.averageTime).as("Average page time should remain reasonable").isLessThan(1000);
        assertThat(cursorResult.dataIntegrityValid).as("Data integrity should be maintained").isTrue();
    }

    @Test
    @DisplayName("Performance comparison: Large dataset (50K records)")
    void testLargeDatasetPerformance() {
        int dataSize = 50000;
        createTestDataset(dataSize);
        
        PerformanceResult cursorResult = measureCursorPaginationPerformance(dataSize, 100, 20);
        
        log.info("=== Large Dataset Performance (50K records) ===");
        log.info("Cursor Pagination - Total: {}ms, Avg per page: {}ms", 
                cursorResult.totalTime, cursorResult.averageTime);
        
        // Performance assertions - should scale well even with large datasets
        assertThat(cursorResult.totalTime).as("Large dataset should scale reasonably").isLessThan(15000);
        assertThat(cursorResult.averageTime).as("Average page time should not degrade significantly").isLessThan(2000);
        assertThat(cursorResult.dataIntegrityValid).as("Data integrity should be maintained").isTrue();
    }

    @Test
    @DisplayName("Deep pagination performance test")
    void testDeepPaginationPerformance() {
        int dataSize = 20000;
        createTestDataset(dataSize);
        
        int pageSize = 50;
        List<Long> pageTimes = new ArrayList<>();
        
        // Test pages at different depths
        int[] testPages = {0, 10, 50, 100, 200, 300}; // From shallow to deep
        
        for (int pageNum : testPages) {
            if (pageNum * pageSize >= dataSize) continue;
            
            long startTime = System.currentTimeMillis();
            
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("viewCount").desc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            long endTime = System.currentTimeMillis();
            long pageTime = endTime - startTime;
            pageTimes.add(pageTime);
            
            log.info("Page {} (offset {}): {}ms, {} results", 
                    pageNum, pageNum * pageSize, pageTime, result.getContent().size());
            
            assertThat(result.getContent()).isNotEmpty();
        }
        
        // Verify that deep pagination doesn't degrade significantly
        long firstPageTime = pageTimes.get(0);
        long lastPageTime = pageTimes.get(pageTimes.size() - 1);
        
        // Cursor pagination should have consistent performance
        double degradationRatio = (double) lastPageTime / firstPageTime;
        assertThat(degradationRatio).as("Deep pagination should not degrade significantly")
                .isLessThan(3.0); // Allow some degradation but not more than 3x
        
        log.info("Deep pagination performance - First page: {}ms, Deep page: {}ms, Ratio: {:.2f}", 
                firstPageTime, lastPageTime, degradationRatio);
    }

    @Test
    @DisplayName("Complex query performance test")
    void testComplexQueryPerformance() {
        int dataSize = 15000;
        createTestDataset(dataSize);
        
        // Test complex query with multiple conditions
        long startTime = System.currentTimeMillis();
        
        SearchCondition<TestPostSearchDTO> complexCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .greaterThan("viewCount", 1000L)
                        .lessThan("viewCount", 50000L)
                )
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(10) // Deep page
                .size(25)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(complexCondition);
        
        long endTime = System.currentTimeMillis();
        long queryTime = endTime - startTime;
        
        log.info("Complex query performance: {}ms, {} results", queryTime, result.getContent().size());
        
        assertThat(queryTime).as("Complex query should be reasonably fast").isLessThan(3000);
        assertThat(result.getContent()).isNotEmpty();
        
        // Verify all results match the complex conditions
        for (TestPost post : result.getContent()) {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount()).isBetween(1000L, 50000L);
            assertThat(post.getTitle()).contains("Post");
        }
    }

    @Test
    @DisplayName("Memory usage stability test")
    void testMemoryUsageStability() {
        int dataSize = 30000;
        createTestDataset(dataSize);
        
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        // Process multiple pages to test memory stability
        for (int page = 0; page < 50; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(page)
                    .size(100)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            if (result.getContent().isEmpty()) {
                break;
            }
            
            // Force garbage collection periodically
            if (page % 10 == 0) {
                System.gc();
                Thread.yield();
            }
        }
        
        System.gc();
        Thread.yield();
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = memoryAfter - memoryBefore;
        
        log.info("Memory usage - Before: {} MB, After: {} MB, Increase: {} MB", 
                memoryBefore / 1024 / 1024, memoryAfter / 1024 / 1024, memoryIncrease / 1024 / 1024);
        
        // Memory increase should be reasonable (less than 100MB for this test)
        assertThat(memoryIncrease).as("Memory usage should be stable").isLessThan(100 * 1024 * 1024);
    }

    @Test
    @DisplayName("Concurrent access performance test")
    void testConcurrentAccessPerformance() {
        int dataSize = 25000;
        createTestDataset(dataSize);
        
        int numberOfThreads = 5;
        int queriesPerThread = 10;
        List<Thread> threads = new ArrayList<>();
        List<Long> allQueryTimes = new ArrayList<>();
        
        long testStartTime = System.currentTimeMillis();
        
        for (int threadId = 0; threadId < numberOfThreads; threadId++) {
            final int currentThreadId = threadId;
            Thread thread = new Thread(() -> {
                List<Long> threadQueryTimes = new ArrayList<>();
                
                for (int queryId = 0; queryId < queriesPerThread; queryId++) {
                    long queryStart = System.currentTimeMillis();
                    
                    SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                            .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                            .sort(s -> s.asc("viewCount").desc("searchTitle"))
                            .page(currentThreadId * 10 + queryId) // Different pages for each query
                            .size(50)
                            .build();

                    Page<TestPost> result = testPostService.findAllWithSearch(condition);
                    
                    long queryEnd = System.currentTimeMillis();
                    long queryTime = queryEnd - queryStart;
                    threadQueryTimes.add(queryTime);
                    
                    assertThat(result).isNotNull();
                }
                
                synchronized (allQueryTimes) {
                    allQueryTimes.addAll(threadQueryTimes);
                }
            });
            
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail("Thread interrupted during concurrent test");
            }
        }
        
        long testEndTime = System.currentTimeMillis();
        long totalTestTime = testEndTime - testStartTime;
        
        double averageQueryTime = allQueryTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long maxQueryTime = allQueryTimes.stream().mapToLong(Long::longValue).max().orElse(0L);
        
        log.info("Concurrent access performance - Total time: {}ms, Avg query: {:.2f}ms, Max query: {}ms", 
                totalTestTime, averageQueryTime, maxQueryTime);
        
        assertThat(totalTestTime).as("Concurrent test should complete in reasonable time").isLessThan(30000);
        assertThat(averageQueryTime).as("Average query time should be reasonable").isLessThan(2000);
        assertThat(maxQueryTime).as("No query should take too long").isLessThan(5000);
    }

    private void createTestDataset(int size) {
        log.info("Creating test dataset with {} records...", size);
        long startTime = System.currentTimeMillis();
        
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 0, 0);
        
        for (int i = 0; i < size; i++) {
            TestPost post = TestPost.builder()
                    .title("Test Post " + String.format("%06d", i))
                    .content("Content for test post number " + i)
                    .status(i % 5 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED)
                    .viewCount((long) ThreadLocalRandom.current().nextInt(1, 100000))
                    .likeCount((long) ThreadLocalRandom.current().nextInt(0, 10000))
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(baseTime.plusMinutes(i))
                    .build();
            
            entityManager.persist(post);
            
            // Batch flush for performance
            if (i % 1000 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }
        
        entityManager.flush();
        entityManager.clear();
        
        long endTime = System.currentTimeMillis();
        log.info("Created {} records in {}ms", size, endTime - startTime);
    }

    private PerformanceResult measureCursorPaginationPerformance(int dataSize, int pageSize, int numberOfPages) {
        List<Long> pageTimes = new ArrayList<>();
        List<List<Long>> pageResults = new ArrayList<>();
        
        long totalStartTime = System.currentTimeMillis();
        
        for (int page = 0; page < numberOfPages; page++) {
            long pageStartTime = System.currentTimeMillis();
            
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("viewCount").desc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            long pageEndTime = System.currentTimeMillis();
            long pageTime = pageEndTime - pageStartTime;
            pageTimes.add(pageTime);
            
            List<Long> pageIds = result.getContent().stream()
                    .map(TestPost::getPostId)
                    .toList();
            pageResults.add(pageIds);
            
            if (result.getContent().isEmpty()) {
                break;
            }
        }
        
        long totalEndTime = System.currentTimeMillis();
        long totalTime = totalEndTime - totalStartTime;
        
        double averageTime = pageTimes.stream().mapToLong(Long::longValue).average().orElse(0.0);
        
        // Verify data integrity
        boolean dataIntegrityValid = verifyDataIntegrity(pageResults);
        
        return new PerformanceResult(totalTime, averageTime, dataIntegrityValid, pageTimes.size());
    }

    private boolean verifyDataIntegrity(List<List<Long>> pageResults) {
        // Check for duplicates across pages
        List<Long> allIds = new ArrayList<>();
        for (List<Long> pageIds : pageResults) {
            for (Long id : pageIds) {
                if (allIds.contains(id)) {
                    log.error("Duplicate ID found: {}", id);
                    return false;
                }
                allIds.add(id);
            }
        }
        return true;
    }

    private static class PerformanceResult {
        final long totalTime;
        final double averageTime;
        final boolean dataIntegrityValid;
        final int pagesProcessed;

        PerformanceResult(long totalTime, double averageTime, boolean dataIntegrityValid, int pagesProcessed) {
            this.totalTime = totalTime;
            this.averageTime = averageTime;
            this.dataIntegrityValid = dataIntegrityValid;
            this.pagesProcessed = pagesProcessed;
        }
    }
} 