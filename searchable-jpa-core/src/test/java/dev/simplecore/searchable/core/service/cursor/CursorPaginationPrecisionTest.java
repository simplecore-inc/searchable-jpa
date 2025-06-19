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
    "spring.datasource.url=jdbc:h2:mem:precision_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@DisplayName("Cursor Pagination Precision Tests")
class CursorPaginationPrecisionTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private EntityManager entityManager;

    private List<TestPost> testPosts;
    private List<TestAuthor> testAuthors;
    private List<TestTag> testTags;

    @BeforeEach
    void setUp() {
        setupTestData();
    }

    private void setupTestData() {
        // Create test authors
        testAuthors = Arrays.asList(
                TestAuthor.builder().name("Alice Author").email("alice@test.com").nickname("alice").build(),
                TestAuthor.builder().name("Bob Author").email("bob@test.com").nickname("bob").build(),
                TestAuthor.builder().name("Charlie Author").email("charlie@test.com").nickname("charlie").build()
        );
        testAuthors.forEach(author -> entityManager.persist(author));

        // Create test tags
        testTags = Arrays.asList(
                TestTag.builder().name("Java").description("Java programming").color("#FF0000").build(),
                TestTag.builder().name("Spring").description("Spring framework").color("#00FF00").build(),
                TestTag.builder().name("JPA").description("Java Persistence API").color("#0000FF").build()
        );
        testTags.forEach(tag -> entityManager.persist(tag));

        // Create test posts with specific viewCount values for predictable sorting
        testPosts = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.now().minusDays(30);
        
        for (int i = 0; i < 50; i++) {
            TestPost post = TestPost.builder()
                    .title("Post " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .viewCount((long) (100 + (i * 10))) // 100, 110, 120, ... 590
                    .status(TestPostStatus.PUBLISHED)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(baseTime.plusHours(i))
                    .build();
            
            // Add tags to some posts
            if (i % 3 == 0) post.addTag(testTags.get(0)); // Java
            if (i % 5 == 0) post.addTag(testTags.get(1)); // Spring
            if (i % 7 == 0) post.addTag(testTags.get(2)); // JPA
            
            testPosts.add(post);
            entityManager.persist(post);
        }
        
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Each page should contain exactly the expected posts with no duplicates")
    void testExactPageContent() {
        int pageSize = 8;
        Set<Long> allSeenPostIds = new HashSet<>();
        List<List<TestPost>> allPages = new ArrayList<>();
        
        // Collect all pages
        for (int pageNum = 0; pageNum < 7; pageNum++) { // 50 posts / 8 per page = ~7 pages
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("viewCount").asc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> page = testPostService.findAllWithSearch(condition);
            List<TestPost> pageContent = page.getContent();
            allPages.add(pageContent);

            System.out.println("=== Page " + pageNum + " ===");
            pageContent.forEach(post -> 
                System.out.println("Post ID: " + post.getPostId() + 
                                 ", ViewCount: " + post.getViewCount() + 
                                 ", Title: " + post.getTitle()));

            // Check for duplicates within the same page
            Set<Long> pagePostIds = pageContent.stream()
                    .map(TestPost::getPostId)
                    .collect(Collectors.toSet());
            
            assertThat(pagePostIds).as("Page %d should not contain duplicate posts", pageNum)
                    .hasSize(pageContent.size());

            // Check for duplicates across pages
            for (Long postId : pagePostIds) {
                assertThat(allSeenPostIds).as("Post ID %d should not appear in multiple pages", postId)
                        .doesNotContain(postId);
                allSeenPostIds.add(postId);
            }

            // Verify sorting within the page
            for (int i = 0; i < pageContent.size() - 1; i++) {
                TestPost current = pageContent.get(i);
                TestPost next = pageContent.get(i + 1);
                
                // viewCount DESC, title ASC
                if (current.getViewCount().equals(next.getViewCount())) {
                    assertThat(current.getTitle()).as("Title should be in ascending order when viewCount is equal")
                            .isLessThanOrEqualTo(next.getTitle());
                } else {
                    assertThat(current.getViewCount()).as("ViewCount should be in descending order")
                            .isGreaterThan(next.getViewCount());
                }
            }
        }

        // Verify total coverage
        assertThat(allSeenPostIds).as("All published posts should be covered across pages")
                .hasSize(50); // We created 50 posts, all published
    }

    @Test
    @DisplayName("Page boundaries should be consistent across multiple queries")
    void testPageBoundaryConsistency() {
        int pageSize = 7;
        
        // Query the same page multiple times
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("viewCount").desc("searchTitle"))
                .page(2) // Third page
                .size(pageSize)
                .build();

        // Query the same page 5 times
        List<Page<TestPost>> multipleQueries = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            Page<TestPost> page = testPostService.findAllWithSearch(condition);
            multipleQueries.add(page);
        }

        // All queries should return identical results
        Page<TestPost> firstQuery = multipleQueries.get(0);
        List<Long> firstQueryIds = firstQuery.getContent().stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toList());

        for (int i = 1; i < multipleQueries.size(); i++) {
            List<Long> currentQueryIds = multipleQueries.get(i).getContent().stream()
                    .map(TestPost::getPostId)
                    .collect(Collectors.toList());
            
            assertThat(currentQueryIds).as("Query %d should return identical results to the first query", i + 1)
                    .isEqualTo(firstQueryIds);
        }

        System.out.println("=== Page 2 Consistency Test ===");
        firstQuery.getContent().forEach(post -> 
            System.out.println("Post ID: " + post.getPostId() + 
                             ", ViewCount: " + post.getViewCount() + 
                             ", Title: " + post.getTitle()));
    }

    @Test
    @DisplayName("Cursor conditions should properly handle edge cases with identical sort values")
    void testIdenticalSortValueHandling() {
        // Create posts with identical viewCount but different titles
        List<TestPost> identicalViewCountPosts = Arrays.asList(
                TestPost.builder()
                        .title("Alpha Post")
                        .content("Content A")
                        .viewCount(999L)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(0))
                        .createdAt(LocalDateTime.now().minusHours(1))
                        .build(),
                TestPost.builder()
                        .title("Beta Post")
                        .content("Content B")
                        .viewCount(999L)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(1))
                        .createdAt(LocalDateTime.now().minusHours(2))
                        .build(),
                TestPost.builder()
                        .title("Gamma Post")
                        .content("Content C")
                        .viewCount(999L)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(2))
                        .createdAt(LocalDateTime.now().minusHours(3))
                        .build()
        );

        identicalViewCountPosts.forEach(post -> entityManager.persist(post));
        entityManager.flush();
        entityManager.clear();

        int pageSize = 2;
        List<TestPost> allResults = new ArrayList<>();
        
        // Collect results from multiple pages
        for (int pageNum = 0; pageNum < 3; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.greaterThan("viewCount", 998L)
                                .and(c -> c.lessThan("viewCount", 1000L)))
                    .sort(s -> s.desc("viewCount").asc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> page = testPostService.findAllWithSearch(condition);
            allResults.addAll(page.getContent());
            
            System.out.println("=== Identical ViewCount Page " + pageNum + " ===");
            page.getContent().forEach(post -> 
                System.out.println("Post ID: " + post.getPostId() + 
                                 ", ViewCount: " + post.getViewCount() + 
                                 ", Title: " + post.getTitle()));
        }

        // Verify all posts are retrieved exactly once
        Set<Long> uniquePostIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        
        assertThat(uniquePostIds).as("All posts with identical viewCount should be retrieved exactly once")
                .hasSize(3);
        
        // Verify sorting is maintained
        assertThat(allResults.get(0).getTitle()).isEqualTo("Alpha Post");
        assertThat(allResults.get(1).getTitle()).isEqualTo("Beta Post");
        assertThat(allResults.get(2).getTitle()).isEqualTo("Gamma Post");
    }

    @Test
    @DisplayName("Large page size should maintain sorting accuracy")
    void testLargePageSortingAccuracy() {
        int largePageSize = 25; // Half of total data
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("createdAt"))
                .page(0)
                .size(largePageSize)
                .build();

        Page<TestPost> page = testPostService.findAllWithSearch(condition);
        List<TestPost> results = page.getContent();
        
        assertThat(results).as("Large page should return expected number of results")
                .hasSize(largePageSize);

        // Verify strict sorting
        for (int i = 0; i < results.size() - 1; i++) {
            TestPost current = results.get(i);
            TestPost next = results.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getCreatedAt()).as("CreatedAt should be in ascending order when viewCount is equal")
                        .isBeforeOrEqualTo(next.getCreatedAt());
            } else {
                assertThat(current.getViewCount()).as("ViewCount should be in descending order")
                        .isGreaterThan(next.getViewCount());
            }
        }

        System.out.println("=== Large Page Test (First 10 results) ===");
        results.stream().limit(10).forEach(post -> 
            System.out.println("Post ID: " + post.getPostId() + 
                             ", ViewCount: " + post.getViewCount() + 
                             ", CreatedAt: " + post.getCreatedAt()));
    }

    @Test
    @DisplayName("Sequential page navigation should maintain perfect continuity")
    void testSequentialPageContinuity() {
        int pageSize = 6;
        List<TestPost> allSequentialResults = new ArrayList<>();
        
        // Navigate through pages sequentially
        for (int pageNum = 0; pageNum < 9; pageNum++) { // 50 posts / 6 per page = ~9 pages
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("viewCount").desc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> page = testPostService.findAllWithSearch(condition);
            allSequentialResults.addAll(page.getContent());
        }

        // Verify perfect sorting continuity across all pages
        for (int i = 0; i < allSequentialResults.size() - 1; i++) {
            TestPost current = allSequentialResults.get(i);
            TestPost next = allSequentialResults.get(i + 1);
            
            // viewCount ASC, title DESC
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getTitle()).as("Title should be in descending order when viewCount is equal at position %d", i)
                        .isGreaterThanOrEqualTo(next.getTitle());
            } else {
                assertThat(current.getViewCount()).as("ViewCount should be in ascending order at position %d", i)
                        .isLessThan(next.getViewCount());
            }
        }

        // Verify no duplicates across all pages
        Set<Long> allPostIds = allSequentialResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        
        assertThat(allPostIds).as("Sequential navigation should cover all posts exactly once")
                .hasSize(allSequentialResults.size())
                .hasSize(50);

        System.out.println("=== Sequential Navigation Test Summary ===");
        System.out.println("Total posts retrieved: " + allSequentialResults.size());
        System.out.println("Unique posts: " + allPostIds.size());
        System.out.println("First post: ID=" + allSequentialResults.get(0).getPostId() + 
                         ", ViewCount=" + allSequentialResults.get(0).getViewCount());
        System.out.println("Last post: ID=" + allSequentialResults.get(allSequentialResults.size()-1).getPostId() + 
                         ", ViewCount=" + allSequentialResults.get(allSequentialResults.size()-1).getViewCount());
    }
} 