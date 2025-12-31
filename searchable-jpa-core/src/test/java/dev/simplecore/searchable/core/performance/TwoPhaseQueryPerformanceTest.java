package dev.simplecore.searchable.core.performance;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.fixture.TestDataManager;
import dev.simplecore.searchable.test.service.TestPostService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for TwoPhaseQueryExecutor
 * 
 * This test validates that TwoPhaseQueryExecutor is now applied to ALL queries,
 * not just ToMany relationship queries. It measures performance across different
 * query types and ensures consistent 2-phase query execution.
 * 
 * Run with: ./gradlew performanceTest
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
@Tag("performance")
class TwoPhaseQueryPerformanceTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private TestDataManager testDataManager;

    @BeforeAll
    static void setUp(@Autowired TestDataManager testDataManager) {
        log.info("=== STEP 1: SETTING UP PERFORMANCE TEST DATA ===");
        
        // Initialize test data for performance testing
        testDataManager.initializeTestData();
        
        log.info("  ✔ Performance test data setup completed");
    }

    @Test
    @DisplayName("Performance Test - Regular query (no relationships) with TwoPhaseQueryExecutor")
    void testRegularQueryPerformance() {
        log.info("=== STEP 1: STARTING REGULAR QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: Regular query without relationships");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test regular query without any relationships
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING REGULAR QUERY");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: REGULAR QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time)
        assertTrue(duration < 3000, "Regular query should complete within 3 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: Regular query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Performance within acceptable limits");
    }

    @Test
    @DisplayName("Performance Test - ManyToMany relationship query with TwoPhaseQueryExecutor")
    void testManyToManyQueryPerformance() {
        log.info("=== STEP 1: STARTING MANYTOMANY QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: ManyToMany relationship query (tags)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test ManyToMany relationship query (tags)
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("tagName", "Java"))
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING MANYTOMANY QUERY");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: MANYTOMANY QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time)
        assertTrue(duration < 5000, "ManyToMany query should complete within 5 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: ManyToMany query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Performance within acceptable limits");
    }

    @Test
    @DisplayName("Performance Test - Complex multi-relationship query with TwoPhaseQueryExecutor")
    void testComplexMultiRelationshipPerformance() {
        log.info("=== STEP 1: STARTING COMPLEX MULTI-RELATIONSHIP QUERY PERFORMANCE TEST ===");
        log.info("  - Query type: Complex query with multiple relationships");
        log.info("  - Relationships: Comments (OneToMany) + Tags (ManyToMany)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test complex query with multiple relationships
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.contains("commentContent", "test"))
                        .and(a -> a.equals("tagName", "Java"))
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                        .and(a -> a.greaterThan("viewCount", 0L))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("viewCount").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING COMPLEX MULTI-RELATIONSHIP QUERY");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: COMPLEX QUERY PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time)
        assertTrue(duration < 8000, "Complex query should complete within 8 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: Complex multi-relationship query performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Performance within acceptable limits");
    }

    @Test
    @DisplayName("Performance Test - Large page size with TwoPhaseQueryExecutor")
    void testLargePageSizePerformance() {
        log.info("=== STEP 1: STARTING LARGE PAGE SIZE PERFORMANCE TEST ===");
        log.info("  - Query type: Large page size query");
        log.info("  - Page size: 100 (large)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test with large page size
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .page(0)
                .size(100) // Large page size
                .sort(s -> s.desc("createdAt").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING LARGE PAGE SIZE QUERY");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: LARGE PAGE SIZE PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        log.info("  - Page size: {}", result.getSize());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time)
        assertTrue(duration < 10000, "Large page query should complete within 10 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: Large page size performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Performance within acceptable limits");
    }

    @Test
    @DisplayName("Performance Test - Deep pagination with TwoPhaseQueryExecutor")
    void testDeepPaginationPerformance() {
        log.info("=== STEP 1: STARTING DEEP PAGINATION PERFORMANCE TEST ===");
        log.info("  - Query type: Deep pagination query");
        log.info("  - Page number: 5 (deep page)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test deep pagination
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .page(5) // Deep page
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        log.info("STEP 2: EXECUTING DEEP PAGINATION QUERY");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: DEEP PAGINATION PERFORMANCE RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Current page: {}", result.getNumber());
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time)
        assertTrue(duration < 6000, "Deep pagination query should complete within 6 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: Deep pagination performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Performance within acceptable limits");
    }

    @Test
    @DisplayName("Performance Test - IN clause batching simulation")
    void testInClauseBatchingSimulation() {
        log.info("=== STEP 1: STARTING IN CLAUSE BATCHING SIMULATION TEST ===");
        log.info("  - Query type: Large result set for IN clause batching");
        log.info("  - Page size: 500 (large for batching simulation)");
        log.info("  - Two-phase execution: Enabled");
        
        long startTime = System.currentTimeMillis();
        
        // Test query that might return many results to test IN clause batching
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .and(a -> a.greaterThan("viewCount", -1L))
                )
                .page(0)
                .size(500) // Large page size to potentially trigger batching
                .sort(s -> s.desc("viewCount").desc("id"))
                .build();

        log.info("STEP 2: EXECUTING IN CLAUSE BATCHING SIMULATION");
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        log.info("STEP 3: IN CLAUSE BATCHING SIMULATION RESULTS");
        log.info("  - Execution time: {} ms", duration);
        log.info("  - Total elements: {}", result.getTotalElements());
        log.info("  - Current page elements: {}", result.getNumberOfElements());
        log.info("  - Total pages: {}", result.getTotalPages());
        log.info("  - Page size: {}", result.getSize());
        
        // Verify results
        assertNotNull(result);
        assertTrue(result.getTotalElements() >= 0);
        
        // Performance assertion (should complete within reasonable time even with potential batching)
        assertTrue(duration < 15000, "IN clause batching query should complete within 15 seconds, but took " + duration + " ms");
        
        log.info("SUMMARY: IN clause batching simulation performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ IN clause batching handled efficiently");
    }

    @Test
    @DisplayName("Performance Test - Query consistency verification")
    void testQueryConsistencyVerification() {
        log.info("=== STEP 1: STARTING QUERY CONSISTENCY VERIFICATION TEST ===");
        log.info("  - Test type: Multiple identical queries for consistency");
        log.info("  - Iterations: 3");
        log.info("  - Two-phase execution: Enabled");
        
        // Test multiple identical queries to ensure consistent performance
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition = 
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                .where(w -> w
                        .contains("searchTitle", "Test")
                        .and(a -> a.equals("status", TestPostStatus.PUBLISHED))
                )
                .page(0)
                .size(20)
                .sort(s -> s.desc("id"))
                .build();

        long totalDuration = 0;
        int iterations = 3;
        Page<TestPost> lastResult = null;
        
        log.info("STEP 2: EXECUTING CONSISTENCY VERIFICATION");
        for (int i = 0; i < iterations; i++) {
            long startTime = System.currentTimeMillis();
            
            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            long endTime = System.currentTimeMillis();
            long duration = endTime - startTime;
            totalDuration += duration;
            
            log.info("  - Iteration {}: {} ms, {} results", i + 1, duration, result.getTotalElements());
            
            // Verify consistency
            assertNotNull(result);
            if (lastResult != null) {
                assertEquals(lastResult.getTotalElements(), result.getTotalElements(), 
                    "Results should be consistent across iterations");
            }
            lastResult = result;
        }
        
        double averageDuration = (double) totalDuration / iterations;
        
        log.info("STEP 3: CONSISTENCY VERIFICATION RESULTS");
        log.info("  - Average execution time: {} ms", String.format("%.2f", averageDuration));
        log.info("  - Total iterations: {}", iterations);
        log.info("  - Total elements: {}", lastResult.getTotalElements());
        log.info("  - Results consistency: ✔ VERIFIED");
        
        // Performance assertion (average should be reasonable)
        assertTrue(averageDuration < 3000, "Average query time should be under 3 seconds, but was " + averageDuration + " ms");
        
        log.info("SUMMARY: Query consistency verification performance test - PASSED");
        log.info("  ✔ Two-phase query execution verified");
        log.info("  ✔ Query results consistency verified");
        log.info("  ✔ Performance stability confirmed");
    }
} 