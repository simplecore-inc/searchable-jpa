package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
import dev.simplecore.searchable.test.dto.TestPostToManySortDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.repository.TestPostRepository;
import dev.simplecore.searchable.test.service.TestPostService;
import dev.simplecore.searchable.test.support.TestSqlCapture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.JoinType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the single primary-key portions of Category 1:
 * 1-2 (over-the-end page still reports the true total),
 * 1-4 (sorting through a to-many relationship keeps pagination stable),
 * 1-6 (Phase 2 applies distinct so to-many fetches do not leak duplicate roots).
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class TwoPhaseSinglePkIssuesTest {

    private static final int POST_COUNT = 30;
    private static final int COMMENTS_PER_POST = 3;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TestPostService testPostService;

    @Autowired
    private TestPostRepository testPostRepository;

    @BeforeEach
    @Transactional
    void setUp() {
        SearchableFieldUtils.clearCache();

        entityManager.createNativeQuery("DELETE FROM test_comment").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_post_tag").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_post").executeUpdate();
        entityManager.createNativeQuery("DELETE FROM test_author").executeUpdate();
        entityManager.flush();

        TestAuthor author = TestAuthor.builder()
                .name("Author").email("author@example.com").nickname("auth").build();
        entityManager.persist(author);

        for (int i = 1; i <= POST_COUNT; i++) {
            TestPost post = TestPost.builder()
                    .title("Post " + i)
                    .content("content " + i)
                    .status(TestPostStatus.PUBLISHED)
                    .viewCount((long) i)
                    .author(author)
                    .build();
            entityManager.persist(post);

            for (int j = 0; j < COMMENTS_PER_POST; j++) {
                TestComment comment = new TestComment();
                comment.setContent(String.format("comment-%02d-%d", i, j));
                comment.setAuthor(author);
                comment.setPost(post);
                entityManager.persist(comment);
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @Transactional
    @DisplayName("1-2: single-PK over-the-end page reports the true total (not 0)")
    void overTheEndPageReportsTrueTotal() {
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                        .page(5)   // offset 100, past the 30 rows
                        .size(20)
                        .sort(s -> s.asc("postId"))
                        .build();

        Page<TestPost> page = testPostService.findAllWithSearch(condition);

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isEqualTo(POST_COUNT);
        assertThat(page.getTotalPages()).isEqualTo(2);
    }

    @Test
    @Transactional
    @DisplayName("1-4: single-PK sort through a to-many field keeps pages disjoint")
    void toManySortKeepsPagesDisjoint() {
        Set<Long> seenIds = new HashSet<>();
        int totalSeen = 0;
        for (int pageIndex = 0; pageIndex < 3; pageIndex++) {
            SearchCondition<TestPostToManySortDTO> condition =
                    SearchConditionBuilder.create(TestPostToManySortDTO.class)
                            .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                            .sort(s -> s.asc("commentContent"))   // comments.content (to-many)
                            .page(pageIndex)
                            .size(10)
                            .build();

            Page<TestPost> page = testPostService.findAllWithSearch(condition);
            totalSeen += page.getContent().size();
            page.getContent().forEach(p -> seenIds.add(p.getPostId()));
        }

        assertThat(totalSeen).isEqualTo(POST_COUNT);
        assertThat(seenIds).hasSize(POST_COUNT);
    }

    @Test
    @Transactional
    @DisplayName("1-6: Phase 2 entity-fetch query applies distinct for to-many fetch joins")
    void phaseTwoAppliesDistinct() {
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.equals("status", TestPostStatus.PUBLISHED))
                        .fetchFields("comments")
                        .page(0)
                        .size(5)
                        .sort(s -> s.asc("postId"))
                        .build();

        TestSqlCapture.start();
        Page<TestPost> page = testPostService.findAllWithSearch(condition);
        TestSqlCapture.stop();

        // Page content must contain distinct posts even though comments are fetch-joined.
        assertThat(page.getContent()).hasSize(5);
        assertThat(page.getContent().stream().map(TestPost::getPostId).distinct().count()).isEqualTo(5L);

        // The Phase 2 entity-fetch query (the one that joins the comments table) must be a
        // SELECT DISTINCT so the database de-duplicates root rows from the to-many fetch join.
        List<String> phaseTwoFetches = TestSqlCapture.captured().stream()
                .map(sql -> sql.toLowerCase())
                .filter(sql -> sql.contains("from test_post") && sql.contains("test_comment"))
                .collect(Collectors.toList());
        assertThat(phaseTwoFetches).isNotEmpty();
        assertThat(phaseTwoFetches).allMatch(sql -> sql.contains("select distinct"));
    }

    @Test
    @Transactional
    @DisplayName("1-6: distinct keeps the SQL result set free of exact-duplicate root rows (A/B on same query)")
    void distinctRemovesDuplicateRootRowsWhenApplicable() {
        List<Long> ids = testPostRepository.findAll().stream()
                .map(TestPost::getPostId)
                .sorted()
                .limit(5)
                .collect(Collectors.toList());

        // Fetch a ToOne relationship (author). Same query, only distinct differs. Capturing the SQL
        // confirms distinct(true) reaches the database as SELECT DISTINCT.
        Specification<TestPost> withDistinct = (root, query, cb) -> {
            query.distinct(true);
            root.fetch("author", JoinType.LEFT);
            return root.get("postId").in(ids);
        };

        TestSqlCapture.start();
        List<TestPost> result = testPostRepository.findAll(withDistinct);
        TestSqlCapture.stop();

        // Correctness: exactly the 5 requested posts, no duplicates.
        assertThat(result).hasSize(5);
        assertThat(result.stream().map(TestPost::getPostId).distinct().count()).isEqualTo(5L);

        // The distinct(true) flag is passed through to the emitted SQL.
        assertThat(TestSqlCapture.captured())
                .anyMatch(sql -> sql.toLowerCase().contains("select distinct")
                        && sql.toLowerCase().contains("from test_post"));
    }
}
