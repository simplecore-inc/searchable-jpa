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
    "spring.datasource.url=jdbc:h2:mem:auto_unique_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
@DisplayName("Cursor Pagination with Automatic Unique Field Addition")
class CursorPaginationAutoUniqueFieldTest {

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private EntityManager entityManager;

    private List<TestAuthor> testAuthors;

    @BeforeEach
    void setUp() {
        createTestData();
    }

    private void createTestData() {
        // Create test authors
        testAuthors = Arrays.asList(
                TestAuthor.builder().name("Alice Author").email("alice@test.com").nickname("alice").build(),
                TestAuthor.builder().name("Bob Author").email("bob@test.com").nickname("bob").build(),
                TestAuthor.builder().name("Charlie Author").email("charlie@test.com").nickname("charlie").build()
        );
        testAuthors.forEach(author -> entityManager.persist(author));

        // Create posts with identical createdAt values to test unique field addition
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0, 0);
        
        for (int i = 0; i < 30; i++) {
            // Create groups of posts with identical createdAt values
            LocalDateTime createdAt = baseTime.plusHours(i / 5); // 5 posts per hour
            
            TestPost post = TestPost.builder()
                    .title("Post Title " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .viewCount((long) (100 + i * 10))
                    .status(TestPostStatus.PUBLISHED)
                    .author(testAuthors.get(i % testAuthors.size()))
                    .createdAt(createdAt)
                    .build();
            
            entityManager.persist(post);
        }
        
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Single field sort should automatically add primary key for uniqueness")
    void testSingleFieldSortWithAutoUniqueField() {
        int pageSize = 5;
        List<TestPost> allResults = new ArrayList<>();
        Set<Long> seenIds = new HashSet<>();
        
        // Test pagination with only createdAt sort (should auto-add ID)
        for (int pageNum = 0; pageNum < 6; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("createdAt"))  // Only one sort field
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            
            System.out.printf("=== Page %d ===\n", pageNum);
            result.getContent().forEach(post -> 
                System.out.printf("Post ID: %d, CreatedAt: %s, Title: %s\n", 
                                post.getPostId(), post.getCreatedAt(), post.getTitle()));
            
            // Verify no duplicates
            for (TestPost post : result.getContent()) {
                assertThat(seenIds).as("Duplicate post ID %d found on page %d", post.getPostId(), pageNum)
                        .doesNotContain(post.getPostId());
                seenIds.add(post.getPostId());
            }
            
            allResults.addAll(result.getContent());
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Verify all posts are unique
        Set<Long> uniqueIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        
        assertThat(uniqueIds).as("All retrieved posts should be unique")
                .hasSize(allResults.size());
        
        // Verify sorting is maintained (createdAt DESC, then ID ASC for ties)
        for (int i = 0; i < allResults.size() - 1; i++) {
            TestPost current = allResults.get(i);
            TestPost next = allResults.get(i + 1);
            
            if (current.getCreatedAt().equals(next.getCreatedAt())) {
                // When createdAt is equal, ID should be in ascending order (auto-added)
                assertThat(current.getPostId()).as("ID should be in ascending order when createdAt is equal")
                        .isLessThan(next.getPostId());
            } else {
                // createdAt should be in descending order
                assertThat(current.getCreatedAt()).as("CreatedAt should be in descending order")
                        .isAfter(next.getCreatedAt());
            }
        }
        
        System.out.printf("Successfully retrieved %d unique posts across %d pages\n", 
                         allResults.size(), (allResults.size() + pageSize - 1) / pageSize);
    }

    @Test
    @DisplayName("Multiple field sort should not duplicate primary key if already present")
    void testMultipleFieldSortWithExistingUniqueField() {
        int pageSize = 4;
        List<TestPost> allResults = new ArrayList<>();
        
        // Test pagination with multiple sort fields including ID
        for (int pageNum = 0; pageNum < 8; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.desc("viewCount").asc("postId"))  // Already includes ID
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Verify no duplicates
        Set<Long> uniqueIds = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toSet());
        
        assertThat(uniqueIds).hasSize(allResults.size());
        
        // Verify sorting: viewCount DESC, then postId ASC
        for (int i = 0; i < allResults.size() - 1; i++) {
            TestPost current = allResults.get(i);
            TestPost next = allResults.get(i + 1);
            
            if (current.getViewCount().equals(next.getViewCount())) {
                assertThat(current.getPostId()).as("PostId should be in ascending order when viewCount is equal")
                        .isLessThan(next.getPostId());
            } else {
                assertThat(current.getViewCount()).as("ViewCount should be in descending order")
                        .isGreaterThan(next.getViewCount());
            }
        }
    }

    @Test
    @DisplayName("Identical sort values should be handled correctly with auto-unique field")
    void testIdenticalSortValuesWithAutoUniqueField() {
        // Create additional posts with identical viewCount and createdAt
        LocalDateTime identicalTime = LocalDateTime.of(2024, 6, 1, 10, 0, 0);
        Long identicalViewCount = 999L;
        
        List<TestPost> identicalPosts = Arrays.asList(
                TestPost.builder()
                        .title("Identical Alpha")
                        .content("Content Alpha")
                        .viewCount(identicalViewCount)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(0))
                        .createdAt(identicalTime)
                        .build(),
                TestPost.builder()
                        .title("Identical Beta")
                        .content("Content Beta")
                        .viewCount(identicalViewCount)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(1))
                        .createdAt(identicalTime)
                        .build(),
                TestPost.builder()
                        .title("Identical Gamma")
                        .content("Content Gamma")
                        .viewCount(identicalViewCount)
                        .status(TestPostStatus.PUBLISHED)
                        .author(testAuthors.get(2))
                        .createdAt(identicalTime)
                        .build()
        );
        
        identicalPosts.forEach(post -> entityManager.persist(post));
        entityManager.flush();
        entityManager.clear();
        
        int pageSize = 2;
        List<TestPost> allResults = new ArrayList<>();
        
        // Search for posts with identical values
        for (int pageNum = 0; pageNum < 3; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.greaterThan("viewCount", identicalViewCount - 1)
                                .and(c -> c.lessThan("viewCount", identicalViewCount + 1)))
                    .sort(s -> s.desc("viewCount").desc("createdAt"))  // Both fields will have identical values
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = testPostService.findAllWithSearch(condition);
            allResults.addAll(result.getContent());
            
            System.out.printf("=== Identical Values Page %d ===\n", pageNum);
            result.getContent().forEach(post -> 
                System.out.printf("Post ID: %d, ViewCount: %d, CreatedAt: %s, Title: %s\n", 
                                post.getPostId(), post.getViewCount(), post.getCreatedAt(), post.getTitle()));
            
            if (!result.hasNext()) {
                break;
            }
        }
        
        // Should retrieve all 3 posts exactly once
        assertThat(allResults).hasSize(3);
        
        Set<String> retrievedTitles = allResults.stream()
                .map(TestPost::getTitle)
                .collect(Collectors.toSet());
        
        assertThat(retrievedTitles).containsExactlyInAnyOrder(
                "Identical Alpha", "Identical Beta", "Identical Gamma");
        
        // Verify they are sorted by ID (auto-added field) since other fields are identical
        List<Long> ids = allResults.stream()
                .map(TestPost::getPostId)
                .collect(Collectors.toList());
        
        assertThat(ids).as("Posts with identical sort values should be sorted by auto-added ID field")
                .isSorted();
    }

    @Test
    @DisplayName("Empty sort should not add unique field")
    void testEmptySortShouldNotAddUniqueField() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                // No sort specified
                .page(0)
                .size(5)
                .build();

        Page<TestPost> result = testPostService.findAllWithSearch(condition);
        
        // Should work without errors (no sort means no cursor pagination issues)
        assertThat(result.getContent()).isNotEmpty();
        assertThat(result.getContent().size()).isLessThanOrEqualTo(5);
    }
} 