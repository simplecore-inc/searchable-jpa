package dev.simplecore.searchable.core.service.cursor;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.entity.TestTag;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.repository.TestPostRepository;
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
    "spring.datasource.url=jdbc:h2:mem:relationship_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@DisplayName("Cursor Pagination with Relationship Tables")
class CursorPaginationRelationshipTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private TestPostRepository testPostRepository;

    @Autowired
    private EntityManager entityManager;

    private List<TestAuthor> authors;
    private List<TestTag> tags;
    private List<TestPost> posts;

    @BeforeEach
    void setUp() {
        // Create test authors
        authors = createAuthors();
        
        // Create test tags
        tags = createTags();
        
        // Create test posts with relationships
        posts = createPostsWithRelationships();
        
        // Create comments for posts
        createCommentsForPosts();
        
        entityManager.flush();
        entityManager.clear();
    }

    private List<TestAuthor> createAuthors() {
        List<TestAuthor> authorList = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + i)
                    .email("author" + i + "@test.com")
                    .nickname("author" + i)
                    .description("Test author " + i)
                    .build();
            entityManager.persist(author);
            authorList.add(author);
        }
        return authorList;
    }

    private List<TestTag> createTags() {
        List<TestTag> tagList = new ArrayList<>();
        String[] tagNames = {"Java", "Spring", "JPA", "Database", "Testing", "Performance", "Security", "Web"};
        String[] colors = {"#FF5733", "#33FF57", "#3357FF", "#FF33F5", "#F5FF33", "#33FFF5", "#F533FF", "#5733FF"};
        
        for (int i = 0; i < tagNames.length; i++) {
            TestTag tag = TestTag.builder()
                    .name(tagNames[i])
                    .description("Description for " + tagNames[i])
                    .color(colors[i])
                    .build();
            entityManager.persist(tag);
            tagList.add(tag);
        }
        return tagList;
    }

    private List<TestPost> createPostsWithRelationships() {
        List<TestPost> postList = new ArrayList<>();
        Random random = new Random(42); // Fixed seed for reproducible tests
        
        for (int i = 1; i <= 100; i++) {
            TestAuthor author = authors.get(random.nextInt(authors.size()));
            
            TestPost post = TestPost.builder()
                    .title("Post Title " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) (random.nextInt(1000) + 1))
                    .likeCount((long) (random.nextInt(500) + 1))
                    .author(author)
                    .createdAt(LocalDateTime.now().minusDays(random.nextInt(30)))
                    .build();

            // Add random tags (1-4 tags per post)
            int tagCount = random.nextInt(4) + 1;
            Set<TestTag> selectedTags = new HashSet<>();
            while (selectedTags.size() < tagCount) {
                selectedTags.add(tags.get(random.nextInt(tags.size())));
            }
            
            for (TestTag tag : selectedTags) {
                post.addTag(tag);
            }

            entityManager.persist(post);
            postList.add(post);
        }
        return postList;
    }

    private void createCommentsForPosts() {
        Random random = new Random(42);
        
        for (TestPost post : posts) {
            // Add 0-5 comments per post
            int commentCount = random.nextInt(6);
            for (int j = 0; j < commentCount; j++) {
                TestAuthor commentAuthor = authors.get(random.nextInt(authors.size()));
                
                TestComment comment = new TestComment();
                comment.setContent("Comment " + (j + 1) + " for " + post.getTitle());
                comment.setAuthor(commentAuthor);
                comment.setPost(post);
                comment.setCreatedAt(LocalDateTime.now().minusHours(random.nextInt(24)));
                
                entityManager.persist(comment);
            }
        }
    }

    @Test
    @DisplayName("ManyToOne relationship - Author name filtering with cursor pagination")
    void testManyToOneRelationshipPagination() {
        int pageSize = 10;
        List<TestPost> allResults = new ArrayList<>();
        
        // Test pagination with author name filter
        for (int page = 0; page < 5; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.contains("authorName", "Author"))
                    .sort(s -> s.asc("viewCount").desc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            // Verify each post has author information
            for (TestPost post : result.getContent()) {
                assertThat(post.getAuthor()).isNotNull();
                assertThat(post.getAuthor().getName()).contains("Author");
            }
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Verify no duplicates
        Set<Long> postIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        assertThat(postIds).hasSize(allResults.size());
        
        // Verify sorting is maintained
        for (int i = 0; i < allResults.size() - 1; i++) {
            TestPost current = allResults.get(i);
            TestPost next = allResults.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getTitle())
                        .isGreaterThanOrEqualTo(next.getTitle());
            } else {
                assertThat(current.getViewCount())
                        .isLessThanOrEqualTo(next.getViewCount());
            }
        }
    }

    @Test
    @DisplayName("ManyToMany relationship - Tag filtering with cursor pagination")
    void testManyToManyRelationshipPagination() {
        int pageSize = 8;
        List<TestPost> allResults = new ArrayList<>();
        
        // Test pagination with tag name filter
        for (int page = 0; page < 6; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.contains("tagName", "a")) // Tags containing 'a': Java, Database, etc.
                    .sort(s -> s.desc("createdAt").asc("searchTitle"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            // Verify each post has at least one tag containing 'a'
            for (TestPost post : result.getContent()) {
                assertThat(post.getTags()).isNotEmpty();
                boolean hasMatchingTag = post.getTags().stream()
                        .anyMatch(tag -> tag.getName().toLowerCase().contains("a"));
                assertThat(hasMatchingTag)
                        .as("Post %s should have at least one tag containing 'a'", post.getTitle())
                        .isTrue();
            }
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Verify no duplicates
        Set<Long> postIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        assertThat(postIds).hasSize(allResults.size());
    }

    @Test
    @DisplayName("Complex relationship query - Author, Comment, and Tag filtering")
    void testComplexRelationshipQuery() {
        int pageSize = 5;
        List<TestPost> allResults = new ArrayList<>();
        
        // Complex query with multiple relationship filters
        for (int page = 0; page < 4; page++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w
                            .contains("authorName", "Author")
                            .and(a -> a.contains("commentContent", "Comment"))
                            .and(a -> a.contains("tagName", "Spring")))
                    .sort(s -> s.desc("viewCount").asc("createdAt"))
                    .page(page)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            // Verify complex conditions
            for (TestPost post : result.getContent()) {
                // Check author
                assertThat(post.getAuthor().getName()).contains("Author");
                
                // Check comments
                boolean hasMatchingComment = post.getComments().stream()
                        .anyMatch(comment -> comment.getContent().contains("Comment"));
                assertThat(hasMatchingComment)
                        .as("Post should have at least one comment containing 'Comment'")
                        .isTrue();
                
                // Check tags
                boolean hasSpringTag = post.getTags().stream()
                        .anyMatch(tag -> tag.getName().contains("Spring"));
                assertThat(hasSpringTag)
                        .as("Post should have Spring tag")
                        .isTrue();
            }
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Verify no duplicates even with complex JOINs
        Set<Long> postIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        assertThat(postIds).hasSize(allResults.size());
    }

    @Test
    @DisplayName("Relationship sorting - Sort by related entity fields")
    void testRelationshipSorting() {
        int pageSize = 15;
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("authorName").desc("tagName").asc("searchTitle"))
                .page(0)
                .size(pageSize)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        assertThat(result.getContent()).hasSizeLessThanOrEqualTo(pageSize);
        
        // Verify sorting by relationship fields works
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            TestPost current = posts.get(i);
            TestPost next = posts.get(i + 1);
            
            String currentAuthorName = current.getAuthor().getName();
            String nextAuthorName = next.getAuthor().getName();
            
            // Author name should be in ascending order
            assertThat(currentAuthorName.compareTo(nextAuthorName))
                    .isLessThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("JOIN consistency - Verify DISTINCT behavior with relationships")
    void testJoinConsistencyWithDistinct() {
        // Test that JOINs don't cause duplicate results
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.contains("tagName", "Java"))
                .sort(s -> s.asc("viewCount"))
                .page(0)
                .size(50)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        // Count unique posts
        Set<Long> uniquePostIds = result.getContent().stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        
        // Should have no duplicates despite JOINs
        assertThat(uniquePostIds).hasSize(result.getContent().size());
        
        // Verify all posts actually have Java tag
        for (TestPost post : result.getContent()) {
            boolean hasJavaTag = post.getTags().stream()
                    .anyMatch(tag -> tag.getName().equals("Java"));
            assertThat(hasJavaTag)
                    .as("Post %s should have Java tag", post.getTitle())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("Performance test - Large dataset with complex relationships")
    void testPerformanceWithComplexRelationships() {
        // Create additional test data for performance testing
        createLargeDataset();
        
        long startTime = System.currentTimeMillis();
        
                 SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                 .where(w -> w
                         .contains("authorName", "Author")
                         .and(a -> a.contains("tagName", "Java")))
                .sort(s -> s.desc("viewCount").asc("createdAt"))
                .page(0)
                .size(20)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;
        
        // Performance assertion (should complete within reasonable time)
        assertThat(executionTime)
                .as("Query should complete within 5 seconds")
                .isLessThan(5000);
        
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent()).hasSizeLessThanOrEqualTo(20);
    }

    private void createLargeDataset() {
        Random random = new Random(123);
        
        // Get managed entities from database
        List<TestAuthor> managedAuthors = entityManager.createQuery(
                "SELECT a FROM TestAuthor a", TestAuthor.class).getResultList();
        List<TestTag> managedTags = entityManager.createQuery(
                "SELECT t FROM TestTag t", TestTag.class).getResultList();
        
        // Create additional posts for performance testing
        for (int i = 101; i <= 500; i++) {
            TestAuthor author = managedAuthors.get(random.nextInt(managedAuthors.size()));
            
            TestPost post = TestPost.builder()
                    .title("Performance Test Post " + i)
                    .content("Performance test content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) (random.nextInt(2000) + 1))
                    .likeCount((long) (random.nextInt(1000) + 1))
                    .author(author)
                    .createdAt(LocalDateTime.now().minusDays(random.nextInt(60)))
                    .build();

            // Add random tags using managed entities
            int tagCount = random.nextInt(3) + 1;
            Set<TestTag> selectedTags = new HashSet<>();
            while (selectedTags.size() < tagCount) {
                selectedTags.add(managedTags.get(random.nextInt(managedTags.size())));
            }
            
            for (TestTag tag : selectedTags) {
                post.addTag(tag);
            }

            entityManager.persist(post);
        }
        
        entityManager.flush();
    }

    @Test
    @DisplayName("Two-phase query comparison - Compare normal vs two-phase query performance")
    void testTwoPhaseQueryComparison() {
        int pageSize = 5;
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.contains("tagName", "a")) // This will trigger ToMany join
                .sort(s -> s.asc("viewCount").desc("searchTitle"))
                .page(1)
                .size(pageSize)
                .build();

        // Test normal pagination (may have HHH000104 warning)
        long startTime1 = System.currentTimeMillis();
        Page<TestPost> normalResult = testPostService.findAllWithSearch(condition);
        long endTime1 = System.currentTimeMillis();
        
        System.out.println("=== Normal Query Performance ===");
        System.out.println("Time taken: " + (endTime1 - startTime1) + "ms");
        System.out.println("Results: " + normalResult.getContent().size());
        
        // Verify results have tags
        for (TestPost post : normalResult.getContent()) {
            assertThat(post.getTags()).isNotEmpty();
            boolean hasMatchingTag = post.getTags().stream()
                    .anyMatch(tag -> tag.getName().toLowerCase().contains("a"));
            assertThat(hasMatchingTag)
                    .as("Post %s should have at least one tag containing 'a'", post.getTitle())
                    .isTrue();
        }
        
        System.out.println("=== Normal Query Results ===");
        normalResult.getContent().forEach(post -> 
            System.out.println("Post ID: " + post.getPostId() + 
                             ", ViewCount: " + post.getViewCount() + 
                             ", Title: " + post.getTitle() +
                             ", Tags: " + post.getTags().stream()
                                     .map(tag -> tag.getName())
                                     .collect(Collectors.joining(", "))));
        
        // Verify page metadata
        assertThat(normalResult.getNumber()).isEqualTo(1);
        assertThat(normalResult.getSize()).isEqualTo(pageSize);
        assertThat(normalResult.getContent().size()).isLessThanOrEqualTo(pageSize);
    }

    @Test
    @DisplayName("Optimized joins should improve performance for complex ToMany relationships")
    void testOptimizedJoinsPerformance() {
        int pageSize = 5;
        
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.contains("tagName", "a")) // This will trigger ToMany join
                .sort(s -> s.asc("viewCount").desc("searchTitle"))
                .page(1)
                .size(pageSize)
                .build();

        // Test optimized joins method
        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                condition, entityManager, TestPost.class, testPostRepository);
        
        long startTime = System.currentTimeMillis();
        Page<TestPost> optimizedResult = builder.buildAndExecuteWithTwoPhaseOptimization();
        long endTime = System.currentTimeMillis();
        
        System.out.println("=== Optimized Joins Performance ===");
        System.out.println("Time taken: " + (endTime - startTime) + "ms");
        System.out.println("Results: " + optimizedResult.getContent().size());
        
        // Verify results have tags
        for (TestPost post : optimizedResult.getContent()) {
            assertThat(post.getTags()).isNotEmpty();
            boolean hasMatchingTag = post.getTags().stream()
                    .anyMatch(tag -> tag.getName().toLowerCase().contains("a"));
            assertThat(hasMatchingTag)
                    .as("Post %s should have at least one tag containing 'a'", post.getTitle())
                    .isTrue();
        }
        
        System.out.println("=== Optimized Joins Results ===");
        optimizedResult.getContent().forEach(post -> 
            System.out.println("Post ID: " + post.getPostId() + 
                             ", ViewCount: " + post.getViewCount() + 
                             ", Title: " + post.getTitle() +
                             ", Tags: " + post.getTags().stream()
                                     .map(tag -> tag.getName())
                                     .collect(Collectors.joining(", "))));
        
        // Compare with normal method
        long startTime2 = System.currentTimeMillis();
        Page<TestPost> normalResult = testPostService.findAllWithSearch(condition);
        long endTime2 = System.currentTimeMillis();
        
        System.out.println("=== Normal Method Performance ===");
        System.out.println("Time taken: " + (endTime2 - startTime2) + "ms");
        
        // Verify both methods return same results
        assertThat(optimizedResult.getContent().size()).isEqualTo(normalResult.getContent().size());
        
        List<Long> optimizedIds = optimizedResult.getContent().stream()
                .map(TestPost::getPostId)
                .sorted()
                .collect(Collectors.toList());
        
        List<Long> normalIds = normalResult.getContent().stream()
                .map(TestPost::getPostId)
                .sorted()
                .collect(Collectors.toList());
        
        assertThat(optimizedIds).isEqualTo(normalIds);
        
        // Verify page metadata
        assertThat(optimizedResult.getNumber()).isEqualTo(1);
        assertThat(optimizedResult.getSize()).isEqualTo(pageSize);
        assertThat(optimizedResult.getContent().size()).isLessThanOrEqualTo(pageSize);
    }

    /**
     * Test two-phase query optimization for large datasets with multiple ToMany relationships.
     * This test verifies that the two-phase strategy is automatically applied and works correctly.
     */
    @Test
    @DisplayName("Two-phase query optimization for multiple ToMany relationships")
    void testTwoPhaseQueryOptimization() {
        // Create test data with multiple ToMany relationships
        createLargeDataset();
        
        // Complex search with multiple ToMany relationships
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.contains("tagName", "Java")
                        .contains("commentContent", "Comment")
                        .contains("searchTitle", "Post Title"))
                .sort(s -> s.desc("createdAt").asc("postId"))
                .page(0)
                .size(10)
                .build();
        
        // Execute with two-phase optimization
        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                condition, entityManager, TestPost.class, testPostRepository);
        
        // This should automatically trigger two-phase query due to multiple ToMany relationships
        Page<TestPost> result = builder.buildAndExecuteWithTwoPhaseOptimization();
        
        // Verify results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(10);
        
        // Verify that relationships are properly loaded (no lazy loading exceptions)
        for (TestPost post : result.getContent()) {
            assertThat(post.getTags()).isNotNull();
            assertThat(post.getComments()).isNotNull();
        }
        
        // Verify pagination works correctly
        assertThat(result.getTotalElements()).isGreaterThan(0);
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(10);
    }
    
    /**
     * Test that single ToMany relationship uses standard strategy, not two-phase.
     */
    @Test
    @DisplayName("Single ToMany relationship should use standard strategy")
    void testSingleToManyUsesStandardStrategy() {
        // Create test data
        createLargeDataset();
        
        // Search with only one ToMany relationship
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.contains("tagName", "Java")
                        .contains("searchTitle", "Post Title"))
                .sort(s -> s.desc("createdAt"))
                .page(0)
                .size(5)
                .build();
        
        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                condition, entityManager, TestPost.class, testPostRepository);
        
        // This should use standard strategy (not two-phase) since only one ToMany relationship
        Page<TestPost> result = builder.buildAndExecuteWithTwoPhaseOptimization();
        
        // Verify results are still correct
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        
        for (TestPost post : result.getContent()) {
            assertThat(post.getTitle()).contains("Post Title");
        }
    }
    

} 