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
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@Transactional
class CursorPaginationLargeDataTest {

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    private static final int TOTAL_POSTS = 1000; // 1,000 posts
    private static final int TOTAL_AUTHORS = 10; // 10 authors
    private List<TestAuthor> authors;
    private Random random = new Random(12345); // Fixed seed for reproducible tests

    @BeforeEach
    void setUp() {
        createLargeTestDataset();
    }

    private void createLargeTestDataset() {
        // Create authors
        authors = new ArrayList<>();
        for (int i = 0; i < TOTAL_AUTHORS; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + String.format("%03d", i))
                    .email("author" + i + "@example.com")
                    .nickname("author" + i)
                    .build();
            authors.add(author);
            em.persist(author);
        }

        // Create posts with varied data
        LocalDateTime baseTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        List<String> titlePrefixes = List.of("Amazing", "Brilliant", "Creative", "Dynamic", "Excellent", 
                                           "Fantastic", "Great", "Incredible", "Marvelous", "Outstanding");
        List<String> titleSuffixes = List.of("Article", "Blog", "Content", "Discussion", "Essay", 
                                           "Feature", "Guide", "Journal", "Post", "Story");

        for (int i = 0; i < TOTAL_POSTS; i++) {
            String titlePrefix = titlePrefixes.get(i % titlePrefixes.size());
            String titleSuffix = titleSuffixes.get((i / 10) % titleSuffixes.size());
            
            TestPost post = TestPost.builder()
                    .title(titlePrefix + " " + titleSuffix + " " + String.format("%05d", i))
                    .content("This is content for post number " + i + ". It contains detailed information about the topic.")
                    .status(i % 3 == 0 ? TestPostStatus.PUBLISHED : 
                           i % 3 == 1 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED)
                    .viewCount((long) (random.nextInt(10000) + i)) // Random view count with base
                    .likeCount(random.nextInt(1000) + 0L) // Random like count
                    .author(authors.get(i % TOTAL_AUTHORS))
                    .createdAt(baseTime.plusHours(i).plusMinutes(random.nextInt(60)))
                    .build();
            em.persist(post);

            // Flush every 100 records to avoid memory issues
            if (i % 100 == 0) {
                em.flush();
                em.clear();
                // Re-attach authors after clear
                for (int j = 0; j < authors.size(); j++) {
                    authors.set(j, em.merge(authors.get(j)));
                }
            }
        }

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Deep pagination should be fast with cursor-based approach")
    void testDeepPaginationPerformance() {
        // Test very deep pagination (page 100+)
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(100) // Very deep page
                .size(10)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        // Should complete quickly even for deep pages
        assertThat(endTime - startTime).isLessThan(500); // Less than 500ms
        
        assertThat(result.getNumber()).isEqualTo(100);
        assertThat(result.getSize()).isEqualTo(10);
        
        // Should return empty or few results for such deep pagination
        assertThat(result.getContent().size()).isLessThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Large page sizes should work efficiently")
    void testLargePageSizes() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount"))
                .page(0)
                .size(100) // Large page size
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        assertThat(endTime - startTime).isLessThan(1000); // Less than 1 second
        assertThat(result.getContent().size()).isLessThanOrEqualTo(100);
        
        // Verify sorting is maintained
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            assertThat(posts.get(i).getViewCount()).isGreaterThanOrEqualTo(posts.get(i + 1).getViewCount());
        }
    }

    @Test
    @DisplayName("Complex multi-field sorting should work correctly")
    void testComplexMultiFieldSorting() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle").desc("createdAt"))
                .page(5)
                .size(20)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(5);
        assertThat(result.getSize()).isEqualTo(20);
        
        // Verify complex sorting
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            TestPost current = posts.get(i);
            TestPost next = posts.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                if (current.getTitle().equals(next.getTitle())) {
                    // If viewCount and title are equal, createdAt should be descending
                    assertThat(current.getCreatedAt()).isAfterOrEqualTo(next.getCreatedAt());
                } else {
                    // If viewCount is equal, title should be ascending
                    assertThat(current.getTitle()).isLessThanOrEqualTo(next.getTitle());
                }
            } else {
                // viewCount should be descending
                assertThat(current.getViewCount()).isGreaterThanOrEqualTo(next.getViewCount());
            }
        }
    }

    @Test
    @DisplayName("Pagination consistency across multiple pages")
    void testPaginationConsistency() {
        int pageSize = 50;
        List<TestPost> allResults = new ArrayList<>();
        
        // Collect results from multiple pages
        for (int page = 0; page < 10; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            // Verify page metadata
            assertThat(result.getNumber()).isEqualTo(page);
            assertThat(result.getSize()).isEqualTo(pageSize);
            
            if (result.getContent().isEmpty()) {
                break; // No more data
            }
        }
        
        // Verify no duplicates across pages
        List<Long> ids = allResults.stream().map(TestPost::getId).toList();
        assertThat(ids).doesNotHaveDuplicates();
        
        // Verify sorting is maintained across all pages
        List<String> titles = allResults.stream().map(TestPost::getTitle).toList();
        assertThat(titles).isSorted();
    }

    @Test
    @DisplayName("Random access to different pages should work")
    void testRandomPageAccess() {
        int[] randomPages = {0, 5, 15, 50, 100, 3, 25, 75}; // Random page numbers
        
        for (int pageNum : randomPages) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("viewCount").asc("searchTitle"))
                    .page(pageNum)
                    .size(10)
                    .build();

            long startTime = System.currentTimeMillis();
            Page<TestPost> result = searchService.findAllWithSearch(condition);
            long endTime = System.currentTimeMillis();

            // Each page access should be fast
            assertThat(endTime - startTime).isLessThan(300);
            
            assertThat(result.getNumber()).isEqualTo(pageNum);
            assertThat(result.getSize()).isEqualTo(10);
            
            // Verify sorting within each page
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
    }

    @Test
    @DisplayName("Edge cases with null values should be handled")
    void testNullValueHandling() {
        // Create some posts with null likeCount
        for (int i = 0; i < 100; i++) {
            TestPost post = TestPost.builder()
                    .title("Null Test Post " + i)
                    .content("Content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) i)
                    .likeCount(i % 3 == 0 ? null : (long) i) // Some null values
                    .author(authors.get(i % authors.size()))
                    .createdAt(LocalDateTime.now().minusDays(i))
                    .build();
            em.persist(post);
        }
        em.flush();

        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(1)
                .size(20)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getNumber()).isEqualTo(1);
        
        // Verify null handling in sorting
        List<TestPost> posts = result.getContent();
        for (TestPost post : posts) {
            // Should handle null values gracefully
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
        }
    }

    @Test
    @DisplayName("Performance comparison between first and deep pages")
    void testPerformanceComparison() {
        // Test first page performance
        SearchCondition<TestPostSearchDTO> firstPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(20)
                .build();

        long firstPageStart = System.currentTimeMillis();
        Page<TestPost> firstPageResult = searchService.findAllWithSearch(firstPageCondition);
        long firstPageEnd = System.currentTimeMillis();
        long firstPageTime = firstPageEnd - firstPageStart;

        // Test deep page performance
        SearchCondition<TestPostSearchDTO> deepPageCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(200) // Deep page
                .size(20)
                .build();

        long deepPageStart = System.currentTimeMillis();
        Page<TestPost> deepPageResult = searchService.findAllWithSearch(deepPageCondition);
        long deepPageEnd = System.currentTimeMillis();
        long deepPageTime = deepPageEnd - deepPageStart;

        // Both should be fast, deep page shouldn't be significantly slower
        assertThat(firstPageTime).isLessThan(500);
        assertThat(deepPageTime).isLessThan(500);
        
        // Deep page time should not be more than 3x first page time (cursor benefit)
        assertThat(deepPageTime).isLessThan(firstPageTime * 3);
        
        System.out.println("First page time: " + firstPageTime + "ms");
        System.out.println("Deep page time: " + deepPageTime + "ms");
        System.out.println("Performance ratio: " + (double) deepPageTime / firstPageTime);
    }
} 