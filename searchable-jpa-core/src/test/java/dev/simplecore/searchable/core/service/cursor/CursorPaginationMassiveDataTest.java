package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.entity.TestTag;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestPostService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:massive_data_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Cursor Pagination with Large Data (10K records)")
class CursorPaginationMassiveDataTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private EntityManager entityManager;

    private static final int TOTAL_POSTS = 10_000; // Start with 10K for initial testing
    private static final int TOTAL_AUTHORS = 50;
    private static final int TOTAL_TAGS = 25;
    private static final int BATCH_SIZE = 500;
    
    private List<TestAuthor> authors;
    private List<TestTag> tags;
    private boolean dataSetupCompleted = false;

    @BeforeEach
    @Transactional
    void setUpMassiveData() {
        if (dataSetupCompleted) {
            return; // Skip if already setup
        }
        System.out.println("=== Starting Large Data Setup (10K records) ===");
        long startTime = System.currentTimeMillis();
        
        createAuthorsAndTags();
        createMassivePosts();
        
        long endTime = System.currentTimeMillis();
        System.out.printf("=== Data Setup Completed in %d ms ===\n", endTime - startTime);
        dataSetupCompleted = true;
    }

    private void createAuthorsAndTags() {
        System.out.println("Creating authors and tags...");
        
        // Create authors in batch
        authors = new ArrayList<>();
        for (int i = 0; i < TOTAL_AUTHORS; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + String.format("%03d", i))
                    .email("author" + i + "@massive.test")
                    .nickname("author" + i)
                    .build();
            authors.add(author);
            entityManager.persist(author);
        }

        // Create tags in batch
        tags = new ArrayList<>();
        String[] tagCategories = {"tech", "business", "science", "art", "sports", "music", "travel", "food"};
        for (int i = 0; i < TOTAL_TAGS; i++) {
            String category = tagCategories[i % tagCategories.length];
            TestTag tag = TestTag.builder()
                    .name(category + "-tag-" + (i / tagCategories.length + 1))
                    .build();
            tags.add(tag);
            entityManager.persist(tag);
        }

        entityManager.flush();
        System.out.printf("Created %d authors and %d tags\n", TOTAL_AUTHORS, TOTAL_TAGS);
    }

    private void createMassivePosts() {
        System.out.println("Creating massive posts dataset...");
        
        // Pre-generate random data for better performance
        ThreadLocalRandom random = ThreadLocalRandom.current();
        LocalDateTime baseTime = LocalDateTime.of(2020, 1, 1, 0, 0);
        
        // Title templates for variety
        String[] titlePrefixes = {
            "Amazing", "Brilliant", "Creative", "Dynamic", "Excellent", "Fantastic", "Great", "Incredible", 
            "Marvelous", "Outstanding", "Perfect", "Quality", "Remarkable", "Superb", "Tremendous",
            "Ultimate", "Valuable", "Wonderful", "eXtraordinary", "Youthful", "Zealous"
        };
        String[] titleSuffixes = {
            "Article", "Blog", "Content", "Discussion", "Essay", "Feature", "Guide", "Handbook", 
            "Insight", "Journal", "Knowledge", "Learning", "Manual", "News", "Overview",
            "Post", "Query", "Resource", "Story", "Tutorial", "Update", "View", "Wisdom", "eXample", "Yearbook"
        };

        // Create posts in batches for optimal memory usage
        for (int batch = 0; batch < TOTAL_POSTS / BATCH_SIZE; batch++) {
            List<TestPost> batchPosts = new ArrayList<>(BATCH_SIZE);
            
            for (int i = 0; i < BATCH_SIZE; i++) {
                int postIndex = batch * BATCH_SIZE + i;
                
                // Generate varied but deterministic data
                String titlePrefix = titlePrefixes[postIndex % titlePrefixes.length];
                String titleSuffix = titleSuffixes[(postIndex / 100) % titleSuffixes.length];
                
                TestPost post = TestPost.builder()
                        .title(titlePrefix + " " + titleSuffix + " " + String.format("%06d", postIndex))
                        .content("Generated content for massive test post #" + postIndex)
                        .status(postIndex % 4 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED) // 75% published
                        .viewCount((long) (random.nextInt(100000) + postIndex)) // Varied view counts
                        .likeCount((long) (random.nextInt(10000) + (postIndex % 1000))) // Some patterns in like counts
                        .author(authors.get(postIndex % TOTAL_AUTHORS))
                        .createdAt(baseTime.plusDays(postIndex / 100).plusHours(postIndex % 24).plusMinutes(postIndex % 60))
                        .build();

                // Add random tags (0-3 tags per post)
                int tagCount = random.nextInt(4);
                if (tagCount > 0) {
                    Set<TestTag> selectedTags = new HashSet<>();
                    while (selectedTags.size() < tagCount) {
                        selectedTags.add(tags.get(random.nextInt(TOTAL_TAGS)));
                    }
                    for (TestTag tag : selectedTags) {
                        post.addTag(tag);
                    }
                }

                batchPosts.add(post);
                entityManager.persist(post);
            }

            // Flush and clear every batch
            entityManager.flush();
            entityManager.clear();
            
            // Re-attach authors and tags after clear
            for (int i = 0; i < authors.size(); i++) {
                authors.set(i, entityManager.merge(authors.get(i)));
            }
            for (int i = 0; i < tags.size(); i++) {
                tags.set(i, entityManager.merge(tags.get(i)));
            }
            
            // Progress reporting
            if ((batch + 1) % 10 == 0) {
                System.out.printf("Processed %d/%d batches (%d posts)\n", 
                    batch + 1, TOTAL_POSTS / BATCH_SIZE, (batch + 1) * BATCH_SIZE);
            }
        }

        System.out.printf("Successfully created %d posts\n", TOTAL_POSTS);
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("First page performance should be excellent")
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
        
        assertThat(endTime - startTime).as("First page should be very fast").isLessThan(200);
        assertThat(result.getContent()).hasSize(20);
        assertThat(result.getTotalElements()).isGreaterThan(7000); // ~75% published
        
        // Verify sorting
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            assertThat(posts.get(i).getViewCount()).isGreaterThanOrEqualTo(posts.get(i + 1).getViewCount());
        }
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Deep pagination should maintain excellent performance")
    void testDeepPaginationPerformance() {
        // Test extremely deep pagination
        int[] deepPages = {50, 100, 200, 300, 400}; // Up to 8,000th record
        
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
            
            // Even deep pages should be fast with cursor-based pagination
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
    @Transactional(readOnly = true)
    @DisplayName("Complex search with relationships should be efficient")
    void testComplexSearchPerformance() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED)
                           .and(c -> c.contains("searchTitle", "Amazing"))
                           .and(c -> c.greaterThan("viewCount", 50000L)))
                .sort(s -> s.desc("viewCount").asc("createdAt"))
                .page(10)
                .size(25)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.printf("Complex search query time: %d ms\n", endTime - startTime);
        System.out.printf("Found %d matching records\n", result.getTotalElements());
        
        assertThat(endTime - startTime).as("Complex search should be efficient").isLessThan(300);
        
        // Verify search results
        result.getContent().forEach(post -> {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getTitle()).containsIgnoringCase("Amazing");
            assertThat(post.getViewCount()).isGreaterThan(50000L);
        });
    }

    @Test
    @Transactional(readOnly = true)
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
            
            assertThat(endTime - startTime).as("Large page size " + pageSize + " should be efficient").isLessThan(500);
            assertThat(result.getContent()).hasSizeLessThanOrEqualTo(pageSize);
            
            // Verify no duplicates
            Set<Long> ids = new HashSet<>();
            for (TestPost post : result.getContent()) {
                assertThat(ids.add(post.getId())).as("No duplicate IDs").isTrue();
            }
        }
    }

    @Test
    @Transactional(readOnly = true)
    @DisplayName("Multi-field sorting should maintain consistency")
    void testMultiFieldSortingConsistency() {
        // Test sorting by multiple fields with potential duplicates
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle").desc("createdAt"))
                .page(50)
                .size(50)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        System.out.printf("Multi-field sorting query time: %d ms\n", endTime - startTime);
        
        assertThat(endTime - startTime).as("Multi-field sorting should be efficient").isLessThan(200);
        
        // Verify complex sorting logic
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
    @Transactional(readOnly = true)
    @DisplayName("Pagination consistency across multiple pages")
    void testPaginationConsistencyAcrossPages() {
        int pageSize = 100;
        int totalPagesToTest = 10;
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
            
            assertThat(endTime - startTime).as("Each page should be fast").isLessThan(150);
            
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
    @Transactional(readOnly = true)
    @DisplayName("Memory usage should remain stable during deep pagination")
    void testMemoryStabilityDuringDeepPagination() {
        Runtime runtime = Runtime.getRuntime();
        long initialMemory = runtime.totalMemory() - runtime.freeMemory();
        
        // Perform multiple deep pagination queries
        for (int i = 0; i < 20; i++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("viewCount"))
                    .page(i * 100) // Increasingly deep pages
                    .size(50)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            assertThat(result).isNotNull();
        }
        
        // Force garbage collection and check memory
        System.gc();
        try {
            Thread.sleep(100); // Give GC time to work
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long finalMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryIncrease = finalMemory - initialMemory;
        
        System.out.printf("Memory usage: Initial=%d MB, Final=%d MB, Increase=%d MB\n",
            initialMemory / 1024 / 1024, finalMemory / 1024 / 1024, memoryIncrease / 1024 / 1024);
        
        // Memory increase should be reasonable (less than 50MB for this test)
        assertThat(memoryIncrease).as("Memory usage should remain stable").isLessThan(50 * 1024 * 1024);
    }
} 