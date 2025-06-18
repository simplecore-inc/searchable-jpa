package dev.simplecore.searchable.core.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;
import dev.simplecore.searchable.test.config.TestConfig;
import dev.simplecore.searchable.test.dto.TestPostDTOs.TestPostSearchDTO;
import dev.simplecore.searchable.test.entity.TestAuthor;
import dev.simplecore.searchable.test.entity.TestComment;
import dev.simplecore.searchable.test.entity.TestPost;
import dev.simplecore.searchable.test.enums.TestPostStatus;
import dev.simplecore.searchable.test.service.TestPostService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ContextConfiguration(classes = TestConfig.class)
@Transactional
class TestPostSearchServiceTest {

    private static final Logger log = LoggerFactory.getLogger(TestPostSearchServiceTest.class);

    @Autowired
    private TestPostService searchService;

    @Autowired
    private EntityManager em;

    private TestPost post1;
    private TestPost post2;
    private TestPost post3;

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

        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 16, 13, 12, 6, 68975000);

        // Create test posts
        post1 = TestPost.builder()
                .title("Important announcement about project")
                .content("Project details...")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(100L)
                .author(author1)
                .createdAt(baseTime.minusDays(1))
                .build();

        post2 = TestPost.builder()
                .title("Technical discussion thread")
                .content("Technical details...")
                .status(TestPostStatus.DRAFT)
                .viewCount(50L)
                .author(author2)
                .createdAt(baseTime.minusDays(2))
                .build();

        post3 = TestPost.builder()
                .title("Welcome to the team")
                .content("Welcome message...")
                .status(TestPostStatus.PUBLISHED)
                .viewCount(200L)
                .author(author1)
                .createdAt(baseTime.minusDays(3))
                .build();

        // Add comments
        TestComment comment1 = new TestComment();
        comment1.setContent("Great announcement!");
        comment1.setAuthor(author2);

        TestComment comment2 = new TestComment();
        comment2.setContent("Looking forward to it");
        comment2.setAuthor(author1);

        post1.addComment(comment1);
        post1.addComment(comment2);

        em.persist(post1);
        em.persist(post2);
        em.persist(post3);
        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("Search posts by title containing specific text")
    void searchByTitleContaining() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .contains("searchTitle", "Important announcement about project"))
                .page(0)
                .size(10)
                .sort(sort -> sort
                        .asc("searchTitle"))
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent())
                .hasSize(1)
                .extracting("title")
                .containsExactly("Important announcement about project");
    }

    @Test
    @DisplayName("Search posts by status with multiple conditions")
    void searchByStatusAndViewCount() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("status", TestPostStatus.PUBLISHED)
                        .and(view -> view
                                .greaterThan("viewCount", 50L)))
                .page(0)
                .size(10)
                .sort(sort -> sort
                        .asc("viewCount"))
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent())
                .hasSize(2)
                .extracting("status")
                .containsOnly(TestPostStatus.PUBLISHED);
    }

    @Test
    @DisplayName("Search posts with nested conditions including joins")
    void searchWithNestedConditionsAndJoins() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("status", TestPostStatus.PUBLISHED)
                        .and(nested -> nested
                                .contains("authorName", "John")
                                .or(comment -> comment
                                        .contains("commentContent", "Great"))))
                .sort(sort -> sort
                        .desc("createdAt"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent())
                .hasSize(2)
                .extracting("title")
                .containsExactlyInAnyOrder(
                        "Important announcement about project",
                        "Welcome to the team");
    }

    @Test
    @DisplayName("Search posts with date range and sorting")
    void searchWithDateRangeAndSorting() {
        LocalDateTime baseTime = LocalDateTime.of(2025, 1, 16, 13, 12, 6, 68975000);
        LocalDateTime startDate = baseTime.minusDays(2);
        LocalDateTime endDate = baseTime;

        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .between("createdAt", startDate, endDate))
                .sort(sort -> sort
                        .desc("viewCount"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent())
                .hasSize(2)
                .extracting("viewCount")
                .containsExactly(100L, 50L);
    }

    @Test
    @DisplayName("Search posts with multiple OR conditions")
    void searchWithOrConditions() {
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .in("status", Arrays.asList(TestPostStatus.PUBLISHED, TestPostStatus.DRAFT)))
                .or(group -> group
                        .contains("authorEmail", "jane@example.com"))
                .sort(sort -> sort
                        .desc("viewCount"))
                .page(0)
                .size(10)
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        assertThat(result.getContent())
                .hasSize(3)
                .extracting("title")
                .containsExactlyInAnyOrder(
                        "Important announcement about project",
                        "Technical discussion thread",
                        "Welcome to the team"
                );
    }

    private TestAuthor createAuthor(String email, String name) {
        TestAuthor author = TestAuthor.builder()
                .email(email)
                .name(name)
                .nickname(name.toLowerCase().replace(" ", ""))
                .build();
        em.persist(author);
        return author;
    }

    private TestPost createPost(TestAuthor author, String title, String content, TestPostStatus status, long viewCount, long likeCount) {
        TestPost post = TestPost.builder()
                .title(title)
                .content(content)
                .status(status)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .author(author)
                .createdAt(LocalDateTime.now())
                .build();
        em.persist(post);
        return post;
    }

    @Test
    @DisplayName("Search posts with complex nested OR/AND conditions")
    void searchWithComplexNestedConditions() {
        // given
        TestAuthor premiumAuthor = createAuthor("premium@test.com", "Premium Author");
        TestAuthor vipAuthor = createAuthor("vip@test.com", "VIP Author");
        TestAuthor regularAuthor = createAuthor("regular@test.com", "Regular Author");

        TestPost post1 = createPost(premiumAuthor, "Premium Content Special Post", "Premium exclusive content", TestPostStatus.PUBLISHED, 600, 100);
        TestPost post2 = createPost(vipAuthor, "VIP Special Content", "VIP only content", TestPostStatus.PUBLISHED, 450, 80);
        TestPost post3 = createPost(regularAuthor, "Regular Post Content Title", "Regular content", TestPostStatus.PUBLISHED, 200, 30);
        TestPost post4 = createPost(premiumAuthor, "Another Premium Post", "More premium content", TestPostStatus.PUBLISHED, 550, 90);
        TestPost post5 = createPost(vipAuthor, "Special VIP Content", "More VIP content", TestPostStatus.PUBLISHED, 350, 70);

        addComment(post1, premiumAuthor, "Great premium content!");
        addComment(post2, vipAuthor, "Excellent VIP post!");
        addComment(post3, regularAuthor, "Nice regular post!");
        addComment(post4, premiumAuthor, "Another great content!");
        addComment(post5, vipAuthor, "Amazing special content!");

        // when
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("status", TestPostStatus.PUBLISHED)
                        .greaterThan("viewCount", 500L)
                        .or(orGroup -> orGroup
                                .contains("searchTitle", "Premium")
                                .orContains("searchTitle", "Special VIP"))
                        .and(andGroup -> andGroup
                                .contains("commentContent", "amazing")
                                .orGreaterThan("viewCount", 300L)))
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // then
        assertThat(result.getContent())
                .hasSize(3)
                .extracting(TestPost::getId)
                .containsExactlyInAnyOrder(post1.getId(), post4.getId(), post5.getId());
    }

    @Test
    @DisplayName("Search posts with extremely complex nested conditions")
    void searchWithSuperComplexNestedConditions() throws JsonProcessingException {
        // Given
        LocalDateTime now = LocalDateTime.now();
        TestAuthor author1 = createAuthor("author1@test.com", "Author One");
        TestAuthor author2 = createAuthor("author2@test.com", "Author Two");
        TestAuthor author3 = createAuthor("author3@test.com", "Author Three");
        TestAuthor author4 = createAuthor("premium@test.com", "Premium User");
        TestAuthor author5 = createAuthor("vip@test.com", "VIP User");

        // Create posts with various conditions
        TestPost post1 = createPost(author1, "Regular Post Content Title", "Normal content", TestPostStatus.PUBLISHED, 150, 25);
        TestPost post2 = createPost(author2, "Premium Content Special Post", "Special premium content", TestPostStatus.PUBLISHED, 500, 80);
        TestPost post3 = createPost(author3, "Special VIP Post Content", "VIP only content", TestPostStatus.DRAFT, 300, 45);
        TestPost post4 = createPost(author4, "Technical Review Post Title", "Technical review content", TestPostStatus.PUBLISHED, 1000, 150);
        TestPost post5 = createPost(author5, "VIP Announcement Post Title", "Important VIP notice", TestPostStatus.PUBLISHED, 800, 120);
        TestPost post6 = createPost(author1, "Regular Update Post Title", "Normal update content", TestPostStatus.DRAFT, 100, 15);
        TestPost post7 = createPost(author2, "Premium Special Post Title", "Premium member special", TestPostStatus.PUBLISHED, 600, 90);
        TestPost post8 = createPost(author4, "Technical Deep Dive Content", "Advanced technical content", TestPostStatus.PUBLISHED, 750, 95);

        // Add comments
        addComment(post1, author2, "Nice post!");
        addComment(post2, author3, "Great premium content!");
        addComment(post3, author5, "Exclusive content");
        addComment(post4, author1, "Very technical");
        addComment(post5, author4, "Important update");

        em.flush();
        em.clear();

        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .greaterThan("viewCount", 499L)
                        .or(specialGroup -> specialGroup
                                .contains("searchTitle", "Premium")
                                .contains("searchTitle", "Special VIP")
                                .and(emailGroup -> emailGroup
                                        .contains("authorEmail", "premium")
                                        .orContains("authorEmail", "vip")))
                        .and(commentGroup -> commentGroup
                                .greaterThan("viewCount", 400L)
                                .or(viewGroup -> viewGroup
                                        .greaterThan("viewCount", 700L)
                                        .and(authorGroup -> authorGroup
                                                .greaterThan("createdAt", now)
                                                .or(vipGroup -> vipGroup
                                                        .contains("authorEmail", "vip")
                                                        .orEquals("status", TestPostStatus.PUBLISHED))))))
                .sort(sort -> sort
                        .desc("viewCount"))
                .page(0)
                .size(20)
                .build();

        // Log the SearchCondition structure as JSON
        log.debug("Search condition: {}", condition.toJson());

        // When
        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // Then
        assertThat(result.getContent())
                .hasSize(5)
                .extracting(TestPost::getId)
                .containsExactlyInAnyOrder(
                        post4.getId(),  // Technical Review Post (1000 views)
                        post5.getId(),  // VIP Announcement (800 views)
                        post8.getId(),  // Technical Deep Dive (750 views)
                        post7.getId(),  // Premium Special Post (600 views)
                        post2.getId()   // Premium Content Special Post (500 views)
                );
    }

    @Test
    @DisplayName("Search posts by id field")
    void searchById() {
        // when
        SearchCondition<TestPostSearchDTO> condition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("id", post1.getId()))
                .build();

        Page<TestPost> result = searchService.findAllWithSearch(condition);

        // then
        assertThat(result.getContent())
                .hasSize(1)
                .extracting("id")
                .containsExactly(post1.getId());
    }

    @Test
    @DisplayName("Search posts by nested entity id (author and comments)")
    void searchByNestedEntityId() {
        // given
        Long authorId = post1.getAuthor().getId();
        Long commentId = post1.getComments().get(0).getId();

        // when - search by author id
        SearchCondition<TestPostSearchDTO> authorCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("authorId", authorId))
                .build();

        Page<TestPost> authorResult = searchService.findAllWithSearch(authorCondition);

        // then - should find all posts by the author
        assertThat(authorResult.getContent())
                .extracting("author.id")
                .containsOnly(authorId);

        // when - search by comment id
        SearchCondition<TestPostSearchDTO> commentCondition = SearchConditionBuilder.create(TestPostSearchDTO.class)
                .where(group -> group
                        .equals("commentId", commentId))
                .build();

        Page<TestPost> commentResult = searchService.findAllWithSearch(commentCondition);

        // then - should find the post containing the comment
        assertThat(commentResult.getContent())
                .hasSize(1)
                .first()
                .satisfies(post -> {
                    assertThat(post.getComments())
                            .extracting("id")
                            .contains(commentId);
                });
    }

    private void addComment(TestPost post, TestAuthor author, String content) {
        TestComment comment = new TestComment();
        comment.setContent(content);
        comment.setAuthor(author);
        post.addComment(comment);
    }

} 