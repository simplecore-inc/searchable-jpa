package dev.simplecore.searchable.core.performance;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.DefaultSearchableService;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.entity.TestPost;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Performance test for ROW_NUMBER() based pagination across multiple page positions.
 * 
 * This test demonstrates the performance characteristics of optimized ROW_NUMBER() pagination
 * with SQL Server dialect optimizations, testing 5 different page positions (0%, 25%, 50%, 75%, 100%).
 * 
 * ⚠ MANUAL EXECUTION ONLY ⚠
 * This test is tagged as 'performance' and excluded from regular test runs.
 * It requires large datasets and can take significant time to complete.
 * 
 * === Manual Test Execution Commands ===
 * 
 * 1. Run performance test with H2 database:
 *    ../gradlew performanceTest -Dspring.profiles.active=test
 * 
 * 2. Run performance test with MSSQL database:
 *    ../gradlew performanceTest -Dspring.profiles.active=mssql
 * 
 * 3. Run specific test method only:
 *    ../gradlew performanceTest --tests "RowNumberPaginationPerformanceTest.testFirstPageVsLastPagePerformance" -Dspring.profiles.active=mssql
 * 
 * === Prerequisites ===
 * - Large dataset (recommended: 5M+ records) for meaningful performance comparison
 * - MSSQL Docker container running (for MSSQL tests): docker run -e "ACCEPT_EULA=Y" -e "SA_PASSWORD=Password123!" -p 1433:1433 mcr.microsoft.com/azure-sql-edge
 * - Generate large dataset: ../gradlew generateLargeData
 * 
 * === Expected Results ===
 * - Tests 5 page positions: First (0%), 25%, 50%, 75%, Last (100%)
 * - Performance ratios compared to first page
 * - Overall assessment: EXCELLENT (<2x), GOOD (<5x), MODERATE (<10x), POOR (≥10x)
 * - SQL Server optimizations: MAXDOP 1, RECOMPILE for large offsets
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class, TestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Tag("performance")
class PaginationPerformanceTest {

    @Autowired
    private DefaultSearchableService<TestPost, Long> searchableService;

    private static final int PAGE_SIZE = 20;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int TEST_ITERATIONS = 3;

    @Test
    @DisplayName("Compare pagination performance across different page positions")
    void testFirstPageVsLastPagePerformance() {
        log.info("=== STEP 1: STARTING MULTI-POSITION PAGINATION PERFORMANCE TEST ===");
        
        // Get total count first with correct page size
        SearchCondition<?> emptyCondition = new SearchCondition<>();
        emptyCondition.setPage(0);
        emptyCondition.setSize(PAGE_SIZE);  // Use actual page size for correct total pages calculation
        Page<TestPost> totalPage = searchableService.findAllWithSearch(emptyCondition);
        long totalElements = totalPage.getTotalElements();
        int totalPages = totalPage.getTotalPages();
        
        log.info("STEP 2: DATASET ANALYSIS");
        log.info("  - Total elements: {}", totalElements);
        log.info("  - Total pages: {}", totalPages);
        log.info("  - Page size: {}", PAGE_SIZE);
        
        if (totalElements < PAGE_SIZE * 5) {
            log.warn("  ⚠ WARNING: Not enough data for meaningful performance test");
            log.warn("  - Required: {} records", PAGE_SIZE * 5);
            log.warn("  - Available: {} records", totalElements);
            log.warn("  - Continuing with available data for testing");
        }

        // Calculate test page positions - test more pages if available
        int[] testPages;
        String[] positionLabels;
        
        if (totalPages >= 10) {
            // Test 10 different positions for comprehensive performance analysis
            testPages = new int[]{
                0,                                    // First page (0%)
                Math.max(1, totalPages / 10),        // 10% position
                Math.max(1, totalPages / 5),         // 20% position
                Math.max(1, totalPages / 4),         // 25% position
                Math.max(1, totalPages / 2),         // 50% position
                Math.max(1, totalPages * 3 / 4),     // 75% position
                Math.max(1, totalPages * 4 / 5),     // 80% position
                Math.max(1, totalPages * 9 / 10),    // 90% position
                Math.max(1, totalPages * 19 / 20),   // 95% position
                Math.max(1, totalPages - 1)          // Last page (100%)
            };
            positionLabels = new String[]{"First (0%)", "10%", "20%", "25%", "50%", "75%", "80%", "90%", "95%", "Last (100%)"};
        } else {
            // Fallback to fewer positions for smaller datasets
            testPages = new int[]{
                0,                                    // First page (0%)
                Math.max(1, totalPages / 4),         // 25% position
                Math.max(1, totalPages / 2),         // 50% position  
                Math.max(1, totalPages * 3 / 4),     // 75% position
                Math.max(1, totalPages - 1)          // Last page (100%)
            };
            positionLabels = new String[]{"First (0%)", "25%", "50%", "75%", "Last (100%)"};
        }
        
        log.info("STEP 3: TEST CONFIGURATION");
        log.info("  - Testing {} page positions: {}", testPages.length, java.util.Arrays.toString(testPages));
        log.info("  - Warmup iterations: {}", WARMUP_ITERATIONS);
        log.info("  - Test iterations per position: {}", TEST_ITERATIONS);

        // Warmup all test pages
        log.info("STEP 4: WARMING UP JVM AND DATABASE CONNECTIONS");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            log.debug("  - Warmup round {}/{}", i + 1, WARMUP_ITERATIONS);
            for (int pageNumber : testPages) {
                performPageQuery(pageNumber);
            }
        }
        log.info("  ✔ Warmup completed");

        // Test performance for each page position
        Map<String, List<Long>> performanceResults = new LinkedHashMap<>();
        Map<String, Integer> pageNumbers = new LinkedHashMap<>();
        
        log.info("STEP 5: EXECUTING PERFORMANCE TESTS");
        for (int i = 0; i < testPages.length; i++) {
            int pageNumber = testPages[i];
            String positionLabel = positionLabels[i];
            
            List<Long> pageTimes = new ArrayList<>();
            log.info("  - Testing {} position (page {}) - {} iterations", 
                positionLabel, pageNumber, TEST_ITERATIONS);
            
            for (int j = 0; j < TEST_ITERATIONS; j++) {
                long startTime = System.nanoTime();
                Page<TestPost> result = performPageQuery(pageNumber);
                long endTime = System.nanoTime();
                long duration = endTime - startTime;
                pageTimes.add(duration);
                
                // Calculate row range for this page
                long startRow = (long) pageNumber * PAGE_SIZE + 1;
                long endRow = startRow + result.getNumberOfElements() - 1;
                
                log.debug("    Iteration {}: {} ms, {} records (rows {}-{})", 
                    j + 1, String.format("%.2f", duration / 1_000_000.0), 
                    result.getNumberOfElements(), startRow, endRow);
            }
            
            performanceResults.put(positionLabel, pageTimes);
            pageNumbers.put(positionLabel, pageNumber);
        }

        // Calculate and log statistics
        log.info("STEP 6: PERFORMANCE ANALYSIS RESULTS");
        
        Map<String, Double> averageTimes = new LinkedHashMap<>();
        double firstPageAvg = 0.0;
        
        for (Map.Entry<String, List<Long>> entry : performanceResults.entrySet()) {
            String position = entry.getKey();
            List<Long> times = entry.getValue();
            int pageNumber = pageNumbers.get(position);
            
            double avgTime = times.stream().mapToLong(Long::longValue).average().orElse(0.0);
            long minTime = times.stream().mapToLong(Long::longValue).min().orElse(0L);
            long maxTime = times.stream().mapToLong(Long::longValue).max().orElse(0L);
            
            averageTimes.put(position, avgTime);
            if (position.equals("First (0%)")) {
                firstPageAvg = avgTime;
            }
            
            // Calculate row range for this page
            long startRow = (long) pageNumber * PAGE_SIZE + 1;
            long endRow = startRow + PAGE_SIZE - 1;
            
            log.info("  {} Position (page {}, rows {}-{}):", position, pageNumber, startRow, endRow);
            log.info("    - Average: {} ms ({} s)", 
                String.format("%.2f", avgTime / 1_000_000.0), 
                String.format("%.3f", avgTime / 1_000_000_000.0));
            log.info("    - Min: {} ms ({} s)", 
                String.format("%.2f", minTime / 1_000_000.0), 
                String.format("%.3f", minTime / 1_000_000_000.0));
            log.info("    - Max: {} ms ({} s)", 
                String.format("%.2f", maxTime / 1_000_000.0), 
                String.format("%.3f", maxTime / 1_000_000_000.0));
        }

        // Calculate and log performance ratios
        log.info("STEP 7: PERFORMANCE RATIO ANALYSIS");
        log.info("  (Compared to first page performance)");
        double maxRatio = 1.0;
        
        for (Map.Entry<String, Double> entry : averageTimes.entrySet()) {
            String position = entry.getKey();
            double avgTime = entry.getValue();
            double ratio = avgTime / firstPageAvg;
            maxRatio = Math.max(maxRatio, ratio);
            
            log.info("  - {}: {}x", position, String.format("%.2f", ratio));
        }
        
        // Overall performance assessment
        log.info("STEP 8: OVERALL PERFORMANCE ASSESSMENT");
        if (maxRatio < 2.0) {
            log.info("  ✔ EXCELLENT: Maximum slowdown is less than 2x");
        } else if (maxRatio < 5.0) {
            log.info("  ✔ GOOD: Maximum slowdown is less than 5x");
        } else if (maxRatio < 10.0) {
            log.warn("  ⚠ MODERATE: Maximum slowdown is {}x", String.format("%.2f", maxRatio));
        } else {
            log.warn("  ✖ POOR: Maximum slowdown is {}x", String.format("%.2f", maxRatio));
        }

        // Assertions
        for (List<Long> times : performanceResults.values()) {
            assertThat(times).hasSize(TEST_ITERATIONS);
            assertThat(times.stream().mapToLong(Long::longValue).average().orElse(0.0)).isGreaterThan(0);
        }
        
        log.info("SUMMARY: Multi-position pagination performance test completed successfully");
        log.info("  - Total positions tested: {}", testPages.length);
        log.info("  - Maximum performance ratio: {}x", String.format("%.2f", maxRatio));
    }

    private Page<TestPost> performPageQuery(int pageNumber) {
        SearchCondition<?> condition = new SearchCondition<>();
        condition.setPage(pageNumber);
        condition.setSize(PAGE_SIZE);
        
        // Add sort order
        SearchCondition.Sort sort = new SearchCondition.Sort();
        sort.addOrder(new SearchCondition.Order("postId", SearchCondition.Direction.ASC));
        condition.setSort(sort);
        
        return searchableService.findAllWithSearch(condition);
    }
} 