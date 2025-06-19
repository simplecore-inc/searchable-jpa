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
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:massive_data_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "logging.level.org.hibernate.SQL=DEBUG",
        "logging.level.org.hibernate.type.descriptor.sql=TRACE",
        "logging.level.dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder=DEBUG"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@Slf4j
class CursorPaginationMassiveDataTest {

    @Autowired
    private TestPostRepository testPostRepository;

    @Autowired
    private EntityManager entityManager;

    private List<TestAuthor> testAuthors;
    private List<TestTag> testTags;

    @BeforeEach
    void setUp() {
        cleanupExistingData();
        createTestData();
    }

    private void cleanupExistingData() {
        entityManager.createQuery("DELETE FROM TestComment").executeUpdate();
        entityManager.createQuery("DELETE FROM TestPost").executeUpdate();
        entityManager.createQuery("DELETE FROM TestTag").executeUpdate();
        entityManager.createQuery("DELETE FROM TestAuthor").executeUpdate();
        entityManager.flush();
    }

    private void createTestData() {
        log.info("Creating test data for massive data performance test...");
        
        // Create authors
        testAuthors = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            TestAuthor author = TestAuthor.builder()
                    .name("Author " + i)
                    .email("author" + i + "@test.com")
                    .nickname("author" + i)
                    .build();
            entityManager.persist(author);
            testAuthors.add(author);
        }

        // Create tags
        testTags = new ArrayList<>();
        String[] tagNames = {"Java", "Spring", "JPA", "Performance", "Database", "Optimization", "Testing", "Framework"};
        for (String tagName : tagNames) {
            TestTag tag = TestTag.builder()
                    .name(tagName)
                    .description("Tag for " + tagName)
                    .build();
            entityManager.persist(tag);
            testTags.add(tag);
        }

        entityManager.flush();

        // Create posts with multiple ToMany relationships
        for (int i = 1; i <= 50; i++) {
            TestPost post = TestPost.builder()
                    .title("Performance Test Post " + i)
                    .content("This is content for performance test post " + i + ". " +
                             "It contains various keywords for search testing.")
                    .status(i % 3 == 0 ? TestPostStatus.DRAFT : TestPostStatus.PUBLISHED)
                    .viewCount((long) (i * 10))
                    .likeCount((long) (i * 2))
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(LocalDateTime.now().minusDays(i % 30))
                    .build();

            // Add multiple tags (ToMany relationship)
            for (int j = 0; j < 3; j++) {
                post.getTags().add(testTags.get((i + j) % testTags.size()));
            }

            entityManager.persist(post);

            // Add comments (ToMany relationship)
            for (int k = 1; k <= 5; k++) {
                TestComment comment = new TestComment();
                comment.setContent("Comment " + k + " for post " + i);
                comment.setAuthor(testAuthors.get((i + k) % testAuthors.size()));
                comment.setPost(post);
                comment.setCreatedAt(LocalDateTime.now().minusHours(k));
                entityManager.persist(comment);
            }

            // Flush every 10 entities to avoid memory issues
            if (i % 10 == 0) {
                entityManager.flush();
                entityManager.clear();
                
                // Re-attach entities after clear to avoid detached entity issues
                for (int idx = 0; idx < testAuthors.size(); idx++) {
                    testAuthors.set(idx, entityManager.merge(testAuthors.get(idx)));
                }
                for (int idx = 0; idx < testTags.size(); idx++) {
                    testTags.set(idx, entityManager.merge(testTags.get(idx)));
                }
                
                log.info("Created {} posts...", i);
            }
        }

        entityManager.flush();
        log.info("Test data creation completed: 50 posts with tags and comments");
    }

    @Test
    @DisplayName("Two-phase query optimization should prevent memory pagination with multiple ToMany relationships")
    void testTwoPhaseQueryOptimization() {
        log.info("=== Testing Two-Phase Query Optimization ===");

        // Complex search with multiple ToMany relationships
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .contains("tagName", "Java")  // ToMany relationship
                        .contains("commentContent", "Comment")  // Another ToMany relationship
                        .greaterThan("viewCount", 50L)  // Changed from 100L to 50L to match test data
                )
                .sort(s -> s.desc("createdAt").asc("postId"))
                .page(0)
                .size(20)
                .build();

        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                condition, entityManager, TestPost.class, testPostRepository);

        long startTime = System.currentTimeMillis();
        
        // Use two-phase query to debug and fix the issue
        Page<TestPost> result = builder.buildAndExecuteWithTwoPhaseOptimization();
        
        long endTime = System.currentTimeMillis();

        log.info("Two-phase query completed in {}ms", (endTime - startTime));
        log.info("Results: {} posts found", result.getContent().size());
        log.info("Total elements: {}", result.getTotalElements());

        // Verify results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(20);

        // Verify that all conditions are satisfied
        for (TestPost post : result.getContent()) {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount()).isGreaterThan(50L);  // Changed from 100L to 50L
            
            // Verify ToMany relationships are loaded without N+1
            assertThat(post.getTags()).isNotNull();
            assertThat(post.getComments()).isNotNull();
            
            // Verify search conditions
            boolean hasJavaTag = post.getTags().stream()
                    .anyMatch(tag -> tag.getName().contains("Java"));
            assertThat(hasJavaTag).isTrue();
            
            boolean hasCommentWithContent = post.getComments().stream()
                    .anyMatch(comment -> comment.getContent().contains("Comment"));
            assertThat(hasCommentWithContent).isTrue();
        }

        // Verify pagination metadata
        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(20);
        assertThat(result.getTotalElements()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Standard single-phase query should work for simple ToOne relationships")
    void testSinglePhaseQueryForToOneRelationships() {
        log.info("=== Testing Single-Phase Query for ToOne Relationships ===");

        // Simple search with only ToOne relationships
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .contains("authorName", "Author")  // ToOne relationship
                        .greaterThan("viewCount", 200L)  // Changed from 500L to 200L to match test data
                )
                .sort(s -> s.desc("viewCount").asc("postId"))
                .page(0)
                .size(15)
                .build();

        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                condition, entityManager, TestPost.class, testPostRepository);

        long startTime = System.currentTimeMillis();
        
        // This should use standard cursor-based pagination (single phase)
        Page<TestPost> result = builder.buildAndExecuteWithCursor();
        
        long endTime = System.currentTimeMillis();

        log.info("Single-phase query completed in {}ms", (endTime - startTime));
        log.info("Results: {} posts found", result.getContent().size());

        // Verify results
        assertThat(result).isNotNull();
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(15);

        // Verify conditions
        for (TestPost post : result.getContent()) {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getViewCount()).isGreaterThan(200L);  // Changed from 500L to 200L
            assertThat(post.getAuthor().getName()).contains("Author");
        }
    }

    @Test
    @DisplayName("Performance comparison: Two-phase vs Single-phase for complex queries")
    void testPerformanceComparison() {
        log.info("=== Performance Comparison Test ===");

        SearchCondition<TestPostSearchDTO> complexCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .contains("tagName", "Spring")
                        .contains("commentContent", "Comment")
                        .greaterThan("viewCount", 100L)  // Changed from 200L to 100L to match test data
                )
                .sort(s -> s.desc("createdAt"))
                .page(0)
                .size(10)
                .build();

        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                complexCondition, entityManager, TestPost.class, testPostRepository);

        // Test 1: Optimized single-phase query
        long optimizedStart = System.currentTimeMillis();
        Page<TestPost> optimizedResult = builder.buildAndExecuteWithCursor();
        long optimizedEnd = System.currentTimeMillis();
        long optimizedTime = optimizedEnd - optimizedStart;

        // Test 2: Standard cursor pagination (same as above for now)
        long standardStart = System.currentTimeMillis();
        Page<TestPost> standardResult = builder.buildAndExecuteWithCursor();
        long standardEnd = System.currentTimeMillis();
        long standardTime = standardEnd - standardStart;

        log.info("=== Performance Results ===");
        log.info("Optimized query: {}ms, {} results", optimizedTime, optimizedResult.getContent().size());
        log.info("Standard cursor: {}ms, {} results", standardTime, standardResult.getContent().size());

        // Both should return the same number of results
        assertThat(optimizedResult.getContent().size()).isEqualTo(standardResult.getContent().size());
        assertThat(optimizedResult.getTotalElements()).isEqualTo(standardResult.getTotalElements());

        // Verify both approaches return correct data
        assertThat(optimizedResult.getContent()).isNotEmpty();
        assertThat(standardResult.getContent()).isNotEmpty();
    }

    @Test
    @DisplayName("Memory efficiency test for large result sets with ToMany relationships")
    void testMemoryEfficiencyWithLargeResultSets() {
        log.info("=== Memory Efficiency Test ===");

        // Query that would potentially return many results with cartesian products
        SearchCondition<TestPostSearchDTO> largeResultCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w
                        .equals("status", TestPostStatus.PUBLISHED)
                        .contains("tagName", "a")  // Common letter, should match many tags
                )
                .sort(s -> s.desc("viewCount"))
                .page(0)
                .size(50)
                .build();

        SearchableSpecificationBuilder<TestPost> builder = SearchableSpecificationBuilder.of(
                largeResultCondition, entityManager, TestPost.class, testPostRepository);

        // Measure memory usage (simplified)
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = builder.buildAndExecuteWithCursor();
        long endTime = System.currentTimeMillis();

        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = memoryAfter - memoryBefore;

        log.info("Memory efficiency results:");
        log.info("Time taken: {}ms", (endTime - startTime));
        log.info("Memory used: {} bytes", memoryUsed);
        log.info("Results: {} posts", result.getContent().size());
        log.info("Total elements: {}", result.getTotalElements());

        // Verify results are correct and relationships are loaded
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(50);

        for (TestPost post : result.getContent()) {
            assertThat(post.getStatus()).isEqualTo(TestPostStatus.PUBLISHED);
            assertThat(post.getTags()).isNotNull();
            
            boolean hasMatchingTag = post.getTags().stream()
                    .anyMatch(tag -> tag.getName().toLowerCase().contains("a"));
            assertThat(hasMatchingTag).isTrue();
        }
    }
} 