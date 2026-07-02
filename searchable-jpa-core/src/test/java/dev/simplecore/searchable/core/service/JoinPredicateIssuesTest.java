package dev.simplecore.searchable.core.service;

import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.core.condition.operator.LogicalOperator;
import dev.simplecore.searchable.core.condition.operator.SearchOperator;
import dev.simplecore.searchable.core.exception.SearchableException;
import dev.simplecore.searchable.core.service.join.JoinManager;
import dev.simplecore.searchable.core.service.specification.PredicateBuilder;
import dev.simplecore.searchable.core.utils.SearchableFieldUtils;
import dev.simplecore.searchable.test.config.BaseTestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs;
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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies Category 4 (join / predicate engine) issues 4-1 through 4-7.
 */
@Slf4j
@SpringBootTest
@ContextConfiguration(classes = {BaseTestConfig.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class JoinPredicateIssuesTest {

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

        TestAuthor boss = TestAuthor.builder().name("Boss").email("boss@example.com").nickname("boss").build();
        entityManager.persist(boss);
        TestAuthor alice = TestAuthor.builder().name("Alice").email("alice@example.com").nickname("alice").build();
        alice.setManager(boss);
        entityManager.persist(alice);

        // Posts for LIKE-escape verification.
        persistPost(alice, "50% discount", 5L);
        persistPost(alice, "50X discount", 5L);
        // Post for the build(Node) group-fold verification.
        persistPost(alice, "keep me", 5L);
        // A few posts with comments for the N+1 verification.
        for (int i = 1; i <= 5; i++) {
            TestPost post = persistPost(alice, "Post " + i, (long) i);
            for (int j = 0; j < 3; j++) {
                TestComment comment = new TestComment();
                comment.setContent("comment-" + i + "-" + j);
                comment.setAuthor(j % 2 == 0 ? alice : boss);
                comment.setPost(post);
                entityManager.persist(comment);
            }
        }

        entityManager.flush();
        entityManager.clear();
    }

    private TestPost persistPost(TestAuthor author, String title, Long viewCount) {
        TestPost post = TestPost.builder()
                .title(title)
                .content(title)
                .status(TestPostStatus.PUBLISHED)
                .viewCount(viewCount)
                .author(author)
                .build();
        entityManager.persist(post);
        return post;
    }

    // ---- 4-1: multi-level ToOne chain in a count query no longer crashes ----

    @Test
    @Transactional
    @DisplayName("4-1: a condition on a 2-level ToOne chain (author.manager.name) does not crash query construction")
    void nestedToOneChainDoesNotCrash() {
        SearchCondition<TestPost> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(
                null, "managerName", SearchOperator.EQUALS, "Boss", null, "author.manager.name"));

        // countWithSearch goes through buildSpecification -> JoinStrategyManager.applyJoins, which is
        // where the dotted ToOne path previously threw IllegalArgumentException.
        long count = testPostService.countWithSearch(condition);

        // All posts are authored by Alice, whose manager is Boss.
        assertThat(count).isEqualTo(testPostRepository.count());
    }

    // ---- 4-4: LIKE ESCAPE makes % / _ literal ----

    @Test
    @Transactional
    @DisplayName("4-4: CONTAINS with a literal '%' matches only the literal, not as a wildcard")
    void likeEscapeTreatsPercentLiterally() {
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.contains("searchTitle", "50%"))
                        .page(0)
                        .size(10)
                        .build();

        Page<TestPost> page = testPostService.findAllWithSearch(condition);

        // Only "50% discount" contains the literal "50%"; "50X discount" must NOT match.
        assertThat(page.getContent()).extracting(TestPost::getTitle).containsExactly("50% discount");
    }

    // ---- 4-5: null value for a pattern operator throws a searchable exception, not NPE ----

    @Test
    @Transactional
    @DisplayName("4-5: a null value on a pattern operator raises a searchable exception, not a NullPointerException")
    void nullPatternValueRejected() {
        // Build a condition programmatically (bypassing validation) with a null CONTAINS value.
        SearchCondition<TestPost> condition = new SearchCondition<>();
        condition.getNodes().add(new SearchCondition.Condition(
                null, "title", SearchOperator.CONTAINS, null, null, "title"));

        assertThatThrownBy(() -> testPostService.findAllWithSearch(condition))
                .satisfies(ex -> assertThat(hasCause(ex, SearchableException.class)).isTrue())
                .satisfies(ex -> assertThat(hasCause(ex, NullPointerException.class)).isFalse());
    }

    // ---- 4-6: PredicateBuilder.build(Group) folds by each child operator ----

    @Test
    @Transactional
    @DisplayName("4-6: PredicateBuilder.build(Group) evaluates mixed AND/OR like the main path")
    void buildNodeGroupFoldsByChildOperator() {
        // Group children: status=PUBLISHED (first), viewCount>1000 (OR), title contains "keep" (AND).
        // Correct left-to-right fold => (PUBLISHED OR viewCount>1000) AND title~keep => title~keep only.
        // The old whole-group-by-one-operator logic (AND) would have produced zero matches.
        List<SearchCondition.Node> children = new ArrayList<>();
        children.add(new SearchCondition.Condition(null, "status", SearchOperator.EQUALS, TestPostStatus.PUBLISHED, null, "status"));
        children.add(new SearchCondition.Condition(LogicalOperator.OR, "viewCount", SearchOperator.GREATER_THAN, 1000L, null, "viewCount"));
        children.add(new SearchCondition.Condition(LogicalOperator.AND, "title", SearchOperator.CONTAINS, "keep", null, "title"));
        SearchCondition.Group group = new SearchCondition.Group(LogicalOperator.AND, children);

        Specification<TestPost> spec = (root, query, cb) -> {
            JoinManager<TestPost> joinManager = new JoinManager<>(entityManager, root);
            PredicateBuilder<TestPost> predicateBuilder = new PredicateBuilder<>(cb, joinManager);
            return predicateBuilder.build(group);
        };

        List<TestPost> results = testPostRepository.findAll(spec);

        assertThat(results).extracting(TestPost::getTitle).containsExactly("keep me");
    }

    // ---- 4-7: BETWEEN converts values correctly (single conversion path) ----

    @Test
    @Transactional
    @DisplayName("4-7: BETWEEN produces correct results with values that require conversion")
    void betweenConvertsValuesCorrectly() {
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.between("viewCount", "2", "4"))
                        .page(0)
                        .size(50)
                        .sort(s -> s.asc("viewCount"))
                        .build();

        Page<TestPost> page = testPostService.findAllWithSearch(condition);

        // Posts with viewCount in [2,4]: the loop created posts with viewCounts 2,3,4 (plus 1 and 5 outside).
        assertThat(page.getContent()).isNotEmpty();
        assertThat(page.getContent()).allSatisfy(post ->
                assertThat(post.getViewCount()).isBetween(2L, 4L));
    }

    // ---- 4-2: nested collection-ToOne (comments[].author) is eagerly loaded -> no N+1 ----

    @Test
    @Transactional
    @DisplayName("4-2: accessing comments[].author after a search triggers no additional queries (N+1 prevented)")
    void nestedCollectionToOnePreventsN1() {
        SearchCondition<TestPostDTOs.TestPostSearchDTO> condition =
                SearchConditionBuilder.create(TestPostDTOs.TestPostSearchDTO.class)
                        .where(w -> w.contains("commentContent", "comment-"))
                        .page(0)
                        .size(20)
                        .sort(s -> s.asc("postId"))
                        .build();

        // Run the search first (fetches performed here), then measure only the access phase.
        Page<TestPost> page = testPostService.findAllWithSearch(condition);

        TestSqlCapture.start();
        int accessedAuthors = 0;
        for (TestPost post : page.getContent()) {
            for (TestComment comment : post.getComments()) {
                // Touch the nested ToOne through the to-many collection.
                assertThat(comment.getAuthor().getName()).isNotBlank();
                accessedAuthors++;
            }
        }
        TestSqlCapture.stop();

        // 5 posts x 3 comments each were matched and their authors touched.
        assertThat(accessedAuthors).isEqualTo(15);
        // Because comments and their authors were fetched during the search, touching them issues
        // zero additional queries (no N+1).
        assertThat(TestSqlCapture.captured()).isEmpty();
    }

    private static boolean hasCause(Throwable throwable, Class<? extends Throwable> type) {
        for (Throwable t = throwable; t != null; t = t.getCause()) {
            if (type.isInstance(t)) {
                return true;
            }
        }
        return false;
    }
}
