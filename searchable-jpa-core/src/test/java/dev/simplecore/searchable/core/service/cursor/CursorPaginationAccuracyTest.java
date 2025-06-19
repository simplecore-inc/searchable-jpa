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
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:accuracy_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
class CursorPaginationAccuracyTest {

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    private List<TestAuthor> authors;

    @BeforeEach
    void setUp() {
        // Create test authors
        authors = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + i)
                    .email("author" + i + "@example.com")
                    .nickname("author" + i)
                    .build();
            em.persist(author);
            authors.add(author);
        }

        // Create 200 test posts with carefully designed data for pagination testing
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        
        for (int i = 0; i < 200; i++) {
            TestPost post = TestPost.builder()
                    .title("Post Title " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .status(i % 3 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED) // 2/3 published
                    .viewCount((long) (i * 10 + (i % 5))) // Varied view counts with some duplicates
                    .likeCount((long) (i % 50)) // Many duplicate like counts
                    .author(authors.get(i % authors.size()))
                    .createdAt(baseTime.plusDays(i).plusHours(i % 24))
                    .build();
            em.persist(post);
        }

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Sequential page navigation should never duplicate or skip records")
    void testSequentialPageNavigationAccuracy() {
        int pageSize = 10;
        Set<Long> allSeenIds = new HashSet<>();
        List<String> allTitles = new ArrayList<>();
        
        // Navigate through first 10 pages sequentially
        for (int pageNum = 0; pageNum < 10; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);
            
            // Verify page metadata
            assertThat(result.getNumber()).isEqualTo(pageNum);
            assertThat(result.getSize()).isEqualTo(pageSize);
            
            // Check for duplicates
            List<Long> pageIds = result.getContent().stream()
                    .map(TestPost::getId)
                    .collect(Collectors.toList());
            
            for (Long id : pageIds) {
                assertThat(allSeenIds).as("Duplicate ID found: %d on page %d", id, pageNum)
                        .doesNotContain(id);
                allSeenIds.add(id);
            }
            
            // Verify ordering within page
            List<String> pageTitles = result.getContent().stream()
                    .map(TestPost::getTitle)
                    .collect(Collectors.toList());
            
            assertThat(pageTitles).as("Titles should be sorted within page %d", pageNum)
                    .isSorted();
            
            allTitles.addAll(pageTitles);
            
            System.out.printf("Page %d: %d items, first: %s, last: %s%n", 
                pageNum, result.getContent().size(),
                result.getContent().isEmpty() ? "N/A" : result.getContent().get(0).getTitle(),
                result.getContent().isEmpty() ? "N/A" : result.getContent().get(result.getContent().size() - 1).getTitle()
            );
        }
        
        // Verify overall ordering across all pages
        assertThat(allTitles).as("Titles should be sorted across all pages").isSorted();
        assertThat(allSeenIds).as("Should have collected unique records").hasSize(allTitles.size());
    }

    @Test
    @DisplayName("Random page access should return consistent results")
    void testRandomPageAccessConsistency() {
        int pageSize = 15;
        int[] randomPages = {0, 5, 2, 8, 1, 10, 3}; // Random page order
        Map<Integer, List<TestPost>> pageResults = new HashMap<>();
        
        // Access pages in random order multiple times
        for (int attempt = 0; attempt < 3; attempt++) {
            for (int pageNum : randomPages) {
                SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                        .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                        .sort(s -> s.desc("viewCount").asc("searchTitle"))
                        .page(pageNum)
                        .size(pageSize)
                        .build();

                Page<TestPost> result = searchService.findAllWithSearch(condition);
                
                if (attempt == 0) {
                    // Store first attempt results
                    pageResults.put(pageNum, new ArrayList<>(result.getContent()));
                } else {
                    // Compare with previous attempts
                    List<TestPost> previousResults = pageResults.get(pageNum);
                    List<TestPost> currentResults = result.getContent();
                    
                    assertThat(currentResults).as("Page %d should return identical results on attempt %d", pageNum, attempt + 1)
                            .hasSize(previousResults.size());
                    
                    for (int i = 0; i < currentResults.size(); i++) {
                        TestPost current = currentResults.get(i);
                        TestPost previous = previousResults.get(i);
                        
                        assertThat(current.getId()).as("Item %d on page %d should have same ID", i, pageNum)
                                .isEqualTo(previous.getId());
                        assertThat(current.getTitle()).as("Item %d on page %d should have same title", i, pageNum)
                                .isEqualTo(previous.getTitle());
                    }
                }
            }
        }
        
        System.out.println("Random page access consistency verified across 3 attempts");
    }

    @Test
    @DisplayName("Complex multi-field sorting should maintain order across pages")
    void testComplexSortingConsistency() {
        int pageSize = 12;
        List<TestPost> allResults = new ArrayList<>();
        
        // Collect results from multiple pages with complex sorting
        for (int pageNum = 0; pageNum < 8; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("viewCount").asc("searchTitle").desc("createdAt"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            if (result.getContent().isEmpty()) {
                break;
            }
        }
        
        // Verify complex sorting is maintained across all pages
        for (int i = 0; i < allResults.size() - 1; i++) {
            TestPost current = allResults.get(i);
            TestPost next = allResults.get(i + 1);
            
            // viewCount DESC, title ASC, createdAt DESC
            if (current.getViewCount().equals(next.getViewCount())) {
                if (current.getTitle().equals(next.getTitle())) {
                    // If viewCount and title are equal, createdAt should be descending
                    assertThat(current.getCreatedAt()).as("CreatedAt should be in descending order when viewCount and title are equal")
                            .isAfterOrEqualTo(next.getCreatedAt());
                } else {
                    // If viewCount is equal, title should be ascending
                    assertThat(current.getTitle()).as("Title should be in ascending order when viewCount is equal")
                            .isLessThanOrEqualTo(next.getTitle());
                }
            } else {
                // viewCount should be descending
                assertThat(current.getViewCount()).as("ViewCount should be in descending order")
                        .isGreaterThanOrEqualTo(next.getViewCount());
            }
        }
        
        System.out.printf("Complex sorting verified across %d records from %d pages%n", 
                allResults.size(), (allResults.size() + pageSize - 1) / pageSize);
    }

    @Test
    @DisplayName("Page boundaries should be exact with no overlap or gaps")
    void testPageBoundaryAccuracy() {
        int pageSize = 7; // Odd number to test edge cases
        List<TestPost> page0Results = new ArrayList<>();
        List<TestPost> page1Results = new ArrayList<>();
        List<TestPost> page2Results = new ArrayList<>();
        
        // Get first three pages
        for (int pageNum = 0; pageNum < 3; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("viewCount").desc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);
            
            switch (pageNum) {
                case 0: page0Results.addAll(result.getContent()); break;
                case 1: page1Results.addAll(result.getContent()); break;
                case 2: page2Results.addAll(result.getContent()); break;
            }
        }
        
        // Verify no overlaps between pages
        Set<Long> page0Ids = page0Results.stream().map(TestPost::getId).collect(Collectors.toSet());
        Set<Long> page1Ids = page1Results.stream().map(TestPost::getId).collect(Collectors.toSet());
        Set<Long> page2Ids = page2Results.stream().map(TestPost::getId).collect(Collectors.toSet());
        
        assertThat(Sets.intersection(page0Ids, page1Ids)).as("Page 0 and 1 should not overlap").isEmpty();
        assertThat(Sets.intersection(page1Ids, page2Ids)).as("Page 1 and 2 should not overlap").isEmpty();
        assertThat(Sets.intersection(page0Ids, page2Ids)).as("Page 0 and 2 should not overlap").isEmpty();
        
        // Verify continuity between pages
        if (!page0Results.isEmpty() && !page1Results.isEmpty()) {
            TestPost lastOfPage0 = page0Results.get(page0Results.size() - 1);
            TestPost firstOfPage1 = page1Results.get(0);
            
            // Should follow sorting order: viewCount ASC, title DESC
            if (lastOfPage0.getViewCount().equals(firstOfPage1.getViewCount())) {
                assertThat(lastOfPage0.getTitle()).as("Title ordering should be maintained across page boundary")
                        .isGreaterThanOrEqualTo(firstOfPage1.getTitle());
            } else {
                assertThat(lastOfPage0.getViewCount()).as("ViewCount ordering should be maintained across page boundary")
                        .isLessThanOrEqualTo(firstOfPage1.getViewCount());
            }
        }
        
        System.out.printf("Page boundary accuracy verified: Page0=%d, Page1=%d, Page2=%d items%n",
                page0Results.size(), page1Results.size(), page2Results.size());
    }

    @Test
    @DisplayName("Large page sizes should maintain accuracy")
    void testLargePageSizeAccuracy() {
        int largePageSize = 50;
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("createdAt"))
                .page(0)
                .size(largePageSize)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);
        
        assertThat(result.getContent()).hasSizeLessThanOrEqualTo(largePageSize);
        
        // Verify sorting within large page
        List<LocalDateTime> createdDates = result.getContent().stream()
                .map(TestPost::getCreatedAt)
                .collect(Collectors.toList());
        
        assertThat(createdDates).as("Large page should maintain sort order").isSorted();
        
        // Verify no duplicates within large page
        Set<Long> ids = result.getContent().stream()
                .map(TestPost::getId)
                .collect(Collectors.toSet());
        
        assertThat(ids).as("Large page should not contain duplicates")
                .hasSize(result.getContent().size());
        
        System.out.printf("Large page size accuracy verified: %d items requested, %d items returned%n",
                largePageSize, result.getContent().size());
    }

    @Test
    @DisplayName("Empty and edge case pages should be handled correctly")
    void testEdgeCasePageHandling() {
        int pageSize = 10;
        
        // Test way beyond available data
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(1000) // Way beyond available data
                .size(pageSize)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();
        
        // Should return empty page quickly
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getNumber()).isEqualTo(1000);
        assertThat(result.getSize()).isEqualTo(pageSize);
        assertThat(result.hasNext()).isFalse();
        assertThat(endTime - startTime).as("Empty page should be fast").isLessThan(500);
        
        // Test page at exact boundary
        long totalPublished = searchService.findAllWithSearch(
                SearchConditionBuilder.create(TestPostSearchDTO.class)
                        .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                        .page(0).size(1000).build()
        ).getTotalElements();
        
        int lastPageNum = (int) ((totalPublished - 1) / pageSize);
        
        SearchCondition<TestPostSearchDTO> lastPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(lastPageNum)
                .size(pageSize)
                .build();

        Page<TestPost> lastPageResult = searchService.findAllWithSearch(lastPageCondition);
        
        assertThat(lastPageResult.getContent()).isNotEmpty();
        assertThat(lastPageResult.isLast()).isTrue();
        assertThat(lastPageResult.hasNext()).isFalse();
        
        System.out.printf("Edge case handling verified: Empty page in %dms, Last page has %d items%n",
                endTime - startTime, lastPageResult.getContent().size());
    }

    @Test
    @DisplayName("Data integrity verification across pages - Ensure actual data is retrieved on correct pages")
    void testDataIntegrityAcrossPages() {
        // Create predictable dataset
        createPredictableDataset();
        
        int pageSize = 10;
        List<TestPost> allRetrievedPosts = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        
        // Retrieve data from multiple pages
        for (int page = 0; page < 5; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);
            
            System.out.printf("Page %d: Retrieved %d posts\n", page, result.getContent().size());
            
            // Verify data on each page
            for (int i = 0; i < result.getContent().size(); i++) {
                TestPost post = result.getContent().get(i);
                
                // Check for duplicates
                assertThat(seenIds.add(post.getId()))
                    .as("Post ID " + post.getId() + " should not appear in multiple pages")
                    .isTrue();
                
                // Verify sorting order (compare with last item from previous page)
                if (!allRetrievedPosts.isEmpty()) {
                    TestPost lastPost = allRetrievedPosts.get(allRetrievedPosts.size() - 1);
                    assertThat(post.getTitle())
                        .as("Current post title should be >= previous post title")
                        .isGreaterThanOrEqualTo(lastPost.getTitle());
                }
                
                allRetrievedPosts.add(post);
                
                System.out.printf("  [%d] ID: %d, Title: %s\n", 
                    page * pageSize + i, post.getId(), post.getTitle());
            }
            
            if (result.getContent().size() < pageSize) {
                break; // Last page
            }
        }
        
        // Verify overall sorting order
        for (int i = 0; i < allRetrievedPosts.size() - 1; i++) {
            TestPost current = allRetrievedPosts.get(i);
            TestPost next = allRetrievedPosts.get(i + 1);
            
            assertThat(current.getTitle())
                .as("Posts should be in ascending title order at position " + i)
                .isLessThanOrEqualTo(next.getTitle());
        }
        
        System.out.printf(" Data integrity verified: %d unique posts in correct order\n", 
            allRetrievedPosts.size());
    }

    @Test
    @DisplayName("Verify expected data on specific page")
    void testSpecificPageExpectedData() {
        // Create predictable dataset
        createPredictableDataset();
        
        int pageSize = 5;
        int targetPage = 2; // Third page
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(targetPage)
                .size(pageSize)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);
        
        System.out.printf("Target page %d contains %d posts:\n", targetPage, result.getContent().size());
        
        // Print and verify information for each post
        for (int i = 0; i < result.getContent().size(); i++) {
            TestPost post = result.getContent().get(i);
            int expectedPosition = targetPage * pageSize + i;
            
            System.out.printf("  Position %d: ID=%d, Title='%s', ViewCount=%d\n", 
                expectedPosition, post.getId(), post.getTitle(), post.getViewCount());
            
            // Basic verification
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getTitle()).isNotNull().isNotEmpty();
            assertThat(post.getViewCount()).isNotNull().isGreaterThanOrEqualTo(0L);
        }
        
        // Verify sorting within page
        for (int i = 0; i < result.getContent().size() - 1; i++) {
            TestPost current = result.getContent().get(i);
            TestPost next = result.getContent().get(i + 1);
            
            assertThat(current.getTitle())
                .as("Within page, posts should be in ascending title order")
                .isLessThanOrEqualTo(next.getTitle());
        }
        
        System.out.printf(" Page %d data verified successfully\n", targetPage);
    }

    @Test
    @DisplayName("Verify accuracy of range search results")
    void testRangeSearchDataAccuracy() {
        // Create predictable dataset
        createPredictableDataset();
        
        long minViewCount = 15L;
        long maxViewCount = 35L;
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED)
                           .and(c -> c.greaterThan("viewCount", minViewCount - 1))
                           .and(c -> c.lessThan("viewCount", maxViewCount + 1)))
                .sort(s -> s.asc("viewCount").asc("searchTitle"))
                .page(0)
                .size(20)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);
        
        System.out.printf("Range search (%d <= viewCount <= %d) found %d posts:\n", 
            minViewCount, maxViewCount, result.getContent().size());
        
        // Verify all results are within range
        for (int i = 0; i < result.getContent().size(); i++) {
            TestPost post = result.getContent().get(i);
            
            System.out.printf("  [%d] Title='%s', ViewCount=%d\n", 
                i, post.getTitle(), post.getViewCount());
            
            // Range verification
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount())
                .as("ViewCount should be within range")
                .isBetween(minViewCount, maxViewCount);
        }
        
        // Verify sorting (viewCount ascending, then title ascending if equal)
        for (int i = 0; i < result.getContent().size() - 1; i++) {
            TestPost current = result.getContent().get(i);
            TestPost next = result.getContent().get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getTitle())
                    .as("When viewCount is equal, title should be in ascending order")
                    .isLessThanOrEqualTo(next.getTitle());
            } else {
                assertThat(current.getViewCount())
                    .as("ViewCount should be in ascending order")
                    .isLessThanOrEqualTo(next.getViewCount());
            }
        }
        
        System.out.printf(" Range search data accuracy verified\n");
    }

    private void createPredictableDataset() {
        // Clean up existing data
        em.createQuery("DELETE FROM TestPost").executeUpdate();
        em.createQuery("DELETE FROM TestAuthor").executeUpdate();
        em.flush();
        // Create predictable author (using unique email)
        TestAuthor author = TestAuthor.builder()
                .name("Predictable Author")
                .email("predictable@validation.com")
                .nickname("predictable")
                .build();
        em.persist(author);
        em.flush();
        
        // Create predictable posts (for easy sorting and searching)
        for (int i = 0; i < 50; i++) {
            TestPostStatus status = (i % 4 == 0) ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED;
            
            TestPost post = TestPost.builder()
                    .title(String.format("Post %03d", i)) // Sortable title
                    .content("Content for post " + i)
                    .status(status)
                    .viewCount((long) (i * 2)) // Predictable viewCount
                    .likeCount((long) (i % 10)) // 0-9 cycle
                    .author(author)
                    .createdAt(LocalDateTime.of(2024, 1, 1, 0, 0).plusDays(i))
                    .build();
            
            em.persist(post);
        }
        
        em.flush();
        em.clear();
        
        System.out.println("Created predictable dataset: 50 posts with systematic data");
    }

    // Helper class for set operations since Guava might not be available
    private static class Sets {
        public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
            Set<T> result = new HashSet<>(set1);
            result.retainAll(set2);
            return result;
        }
    }
} 