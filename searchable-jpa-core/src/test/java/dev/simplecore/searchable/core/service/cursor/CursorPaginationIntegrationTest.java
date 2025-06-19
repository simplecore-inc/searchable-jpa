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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:integration_test_db;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL;LOCK_MODE=0",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
@Transactional
class CursorPaginationIntegrationTest {

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    @BeforeEach
    void setUp() {
        // Create test authors
        TestAuthor author1 = TestAuthor.builder()
                .name("John Doe")
                .email("john@example.com")
                .nickname("johndoe")
                .build();
        TestAuthor author2 = TestAuthor.builder()
                .name("Jane Smith")
                .email("jane@example.com")
                .nickname("janesmith")
                .build();
        em.persist(author1);
        em.persist(author2);

        // Create 50 test posts for pagination testing
        List<TestPost> posts = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2024, 1, 1, 12, 0);
        
        for (int i = 0; i < 50; i++) {
            TestPost post = TestPost.builder()
                    .title("Post Title " + String.format("%03d", i))
                    .content("Content for post " + i)
                    .status(i % 2 == 0 ? TestPostStatus.PUBLISHED : TestPostStatus.DRAFT)
                    .viewCount((long) (i * 10))
                    .author(i % 2 == 0 ? author1 : author2)
                    .createdAt(baseTime.plusDays(i))
                    .build();
            posts.add(post);
            em.persist(post);
        }

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("First page should work with cursor-based pagination")
    void testFirstPageCursorPagination() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(0)
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(0);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isFalse();
        assertThat(result.isFirst()).isTrue();
        assertThat(result.isLast()).isFalse();
    }

    @Test
    @DisplayName("Middle pages should work with cursor-based pagination")
    void testMiddlePageCursorPagination() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(2) // Third page
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.hasNext()).isTrue();
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isFalse();
        
        // Verify content is correctly ordered
        List<String> titles = result.getContent().stream()
                .map(TestPost::getTitle)
                .toList();
        assertThat(titles).isSorted();
    }

    @Test
    @DisplayName("Last page should work with cursor-based pagination")
    void testLastPageCursorPagination() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(4) // Last page (25 published posts / 5 per page = 5 pages, 0-4)
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(4);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).hasSize(5);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("Deep pagination should work efficiently with cursor-based approach")
    void testDeepPaginationPerformance() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(10) // Very deep page that would be slow with OFFSET
                .size(2)
                .build();

        long startTime = System.currentTimeMillis();
        Page<TestPost> result = searchService.findAllWithSearch(condition);
        long endTime = System.currentTimeMillis();

        // Should complete quickly even for deep pages
        assertThat(endTime - startTime).isLessThan(1000); // Less than 1 second
        
        assertThat(result.getNumber()).isEqualTo(10);
        assertThat(result.getSize()).isEqualTo(2);
        // May have fewer results if we're past the end
        assertThat(result.getContent().size()).isLessThanOrEqualTo(2);
    }

    @Test
    @DisplayName("Complex sorting should work with cursor-based pagination")
    void testComplexSortingCursorPagination() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.desc("viewCount").asc("searchTitle"))
                .page(1)
                .size(3)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(1);
        assertThat(result.getSize()).isEqualTo(3);
        assertThat(result.getContent()).hasSize(3);
        
        // Verify complex sorting is maintained
        List<TestPost> posts = result.getContent();
        for (int i = 0; i < posts.size() - 1; i++) {
            TestPost current = posts.get(i);
            TestPost next = posts.get(i + 1);
            
            // viewCount should be descending
            if (current.getViewCount().equals(next.getViewCount())) {
                // If viewCount is equal, title should be ascending
                assertThat(current.getTitle()).isLessThanOrEqualTo(next.getTitle());
            } else {
                assertThat(current.getViewCount()).isGreaterThanOrEqualTo(next.getViewCount());
            }
        }
    }

    @Test
    @DisplayName("Empty pages should be handled correctly")
    void testEmptyPageHandling() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                .sort(s -> s.asc("searchTitle"))
                .page(100) // Way beyond available data
                .size(5)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getNumber()).isEqualTo(100);
        assertThat(result.getSize()).isEqualTo(5);
        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.hasPrevious()).isTrue();
        assertThat(result.isFirst()).isFalse();
        assertThat(result.isLast()).isTrue();
    }

    @Test
    @DisplayName("Page metadata should be consistent across all pages")
    void testPageMetadataConsistency() {
        int pageSize = 5;
        
        // Test multiple pages to ensure metadata consistency
        for (int pageNum = 0; pageNum < 3; pageNum++) {
            SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                    .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                    .sort(s -> s.asc("searchTitle"))
                    .page(pageNum)
                    .size(pageSize)
                    .build();

            Page<TestPost> result = searchService.findAllWithSearch(condition);

            // Verify basic metadata
            assertThat(result.getNumber()).isEqualTo(pageNum);
            assertThat(result.getSize()).isEqualTo(pageSize);
            
            // Verify navigation metadata
            assertThat(result.hasPrevious()).isEqualTo(pageNum > 0);
            assertThat(result.isFirst()).isEqualTo(pageNum == 0);
            
            // Verify total elements is consistent (should be 25 published posts)
            assertThat(result.getTotalElements()).isEqualTo(25);
            assertThat(result.getTotalPages()).isEqualTo(5); // 25 / 5 = 5 pages
        }
    }
} 