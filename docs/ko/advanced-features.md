# 고급 기능

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 검색 연산자](search-operators.md) | [다음: 커서 페이징](cursor-pagination.md)

---

이 문서는 Searchable JPA의 고급 기능들을 설명합니다. 복잡한 검색 조건, 중첩 쿼리, 프로그래매틱 빌더 패턴 등을 다룹니다.

## SearchConditionBuilder를 사용한 프로그래매틱 검색 조건 구성

코드에서 동적으로 검색 조건을 구성할 때 `SearchConditionBuilder`를 사용할 수 있습니다.

### 기본 사용법

```java
@Service
public class PostService extends DefaultSearchableService<Post> {
    
    public Page<Post> findPublishedPostsByAuthor(String authorName) {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("status", PostStatus.PUBLISHED)
                .contains("authorName", authorName)
            )
            .sort(sort -> sort
                .desc("createdAt")
                .asc("title")
            )
            .page(0)
            .size(20)
            .build();
            
        return findAllWithSearch(condition);
    }
}
```

### 조건부 검색 조건 추가

```java
public Page<Post> searchPosts(String title, PostStatus status, LocalDateTime fromDate) {
    ChainedSearchCondition<PostSearchDTO> builder = SearchConditionBuilder
        .create(PostSearchDTO.class);
    
    // 제목이 제공된 경우에만 추가
    if (title != null && !title.isEmpty()) {
        builder = builder.where(group -> group.contains("title", title));
    }
    
    // 상태가 제공된 경우에만 추가
    if (status != null) {
        builder = builder.where(group -> group.equals("status", status));
    }
    
    // 날짜가 제공된 경우에만 추가
    if (fromDate != null) {
        builder = builder.where(group -> group.greaterThan("createdAt", fromDate));
    }
    
    SearchCondition<PostSearchDTO> condition = builder
        .sort(sort -> sort.desc("createdAt"))
        .page(0)
        .size(10)
        .build();
        
    return findAllWithSearch(condition);
}
```

## 복잡한 논리 조건 구성

### AND와 OR 조건 조합

```java
// (title CONTAINS 'Spring' OR title CONTAINS 'Java') AND status = 'PUBLISHED'
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(group -> group
        .contains("title", "Spring")
    )
    .or(group -> group
        .contains("title", "Java")
    )
    .and(group -> group
        .equals("status", PostStatus.PUBLISHED)
    )
    .build();
```

### 중첩된 그룹 조건

```java
// ((title CONTAINS 'Spring' AND viewCount > 100) OR (authorName = 'John')) AND status = 'PUBLISHED'
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(group -> group
        .contains("title", "Spring")
        .greaterThan("viewCount", 100L)
    )
    .or(group -> group
        .equals("authorName", "John")
    )
    .and(group -> group
        .equals("status", PostStatus.PUBLISHED)
    )
    .build();
```

## 중첩 엔티티 검색

### 연관 엔티티 필드 검색

```java
public class PostSearchDTO {
    // 작성자의 이름으로 검색
    @SearchableField(entityField = "author.name", operators = {CONTAINS, EQUALS})
    private String authorName;
    
    // 작성자의 이메일로 검색
    @SearchableField(entityField = "author.email", operators = {CONTAINS, EQUALS})
    private String authorEmail;
    
    // 댓글 내용으로 검색 (OneToMany 관계)
    @SearchableField(entityField = "comments.content", operators = {CONTAINS})
    private String commentContent;
}
```

### 깊은 중첩 관계 검색

```java
public class PostSearchDTO {
    // 작성자의 부서명으로 검색 (author.department.name)
    @SearchableField(entityField = "author.department.name", operators = {EQUALS, CONTAINS})
    private String authorDepartmentName;
    
    // 작성자의 회사명으로 검색 (author.department.company.name)
    @SearchableField(entityField = "author.department.company.name", operators = {EQUALS})
    private String companyName;
}
```

## 동적 정렬

### 프로그래매틱 정렬

```java
public Page<Post> searchWithDynamicSort(String sortField, String sortDirection) {
    SortBuilder sortBuilder = SortBuilder.create();
    
    if ("asc".equalsIgnoreCase(sortDirection)) {
        sortBuilder.asc(sortField);
    } else {
        sortBuilder.desc(sortField);
    }
    
    SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
        .create(PostSearchDTO.class)
        .sort(sort -> sortBuilder)
        .build();
        
    return findAllWithSearch(condition);
}
```

### 다중 필드 정렬

```java
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .sort(sort -> sort
        .desc("status")      // 상태별 내림차순
        .desc("createdAt")   // 생성일별 내림차순
        .asc("title")        // 제목별 오름차순
    )
    .build();
```

## 업데이트 및 삭제 작업

### 조건부 업데이트

```java
@Service
public class PostService extends DefaultSearchableService<Post> {
    
    @Transactional
    public long updatePostStatusByAuthor(String authorEmail, PostStatus newStatus) {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("authorEmail", authorEmail)
            )
            .build();
            
        PostUpdateDTO updateData = new PostUpdateDTO();
        updateData.setStatus(newStatus);
        
        return updateWithSearch(condition, updateData);
    }
    
    @Transactional
    public long deleteOldDraftPosts(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("status", PostStatus.DRAFT)
                .lessThan("createdAt", cutoffDate)
            )
            .build();
            
        return deleteWithSearch(condition);
    }
}
```

## DTO 프로젝션

### 인터페이스 기반 프로젝션

```java
public interface PostListProjection {
    Long getId();
    String getTitle();
    PostStatus getStatus();
    Long getViewCount();
    LocalDateTime getCreatedAt();
    Author getAuthor();
}

// 서비스에서 사용
public Page<PostListProjection> findPostList(SearchCondition<PostSearchDTO> condition) {
    return findAllWithSearch(condition, PostListProjection.class);
}
```

### 클래스 기반 프로젝션

```java
public class PostSummaryDTO {
    private Long id;
    private String title;
    private String authorName;
    private LocalDateTime createdAt;
    
    public PostSummaryDTO(Long id, String title, String authorName, LocalDateTime createdAt) {
        this.id = id;
        this.title = title;
        this.authorName = authorName;
        this.createdAt = createdAt;
    }
    
    // getters...
}

// 서비스에서 사용
public Page<PostSummaryDTO> findPostSummaries(SearchCondition<PostSearchDTO> condition) {
    return findAllWithSearch(condition, PostSummaryDTO.class);
}
```

## 커스텀 검증

### SearchCondition 검증

```java
@Component
public class PostSearchValidator {
    
    public void validate(SearchCondition<PostSearchDTO> condition) {
        // 페이지 크기 제한
        if (condition.getSize() != null && condition.getSize() > 100) {
            throw new IllegalArgumentException("페이지 크기는 100을 초과할 수 없습니다.");
        }
        
        // 필수 조건 확인
        boolean hasStatusCondition = condition.getNodes().stream()
            .anyMatch(node -> node instanceof SearchCondition.Condition && 
                     "status".equals(((SearchCondition.Condition) node).getField()));
                     
        if (!hasStatusCondition) {
            throw new IllegalArgumentException("상태 조건은 필수입니다.");
        }
    }
}
```

## 성능 최적화

### 인덱스 힌트

```java
public class PostSearchDTO {
    // 인덱스가 있는 필드에 대한 검색을 우선적으로 사용
    @SearchableField(operators = {EQUALS}, sortable = true)
    private Long id;  // Primary Key
    
    @SearchableField(operators = {EQUALS, IN}, sortable = true)
    private PostStatus status;  // 인덱스가 있는 필드
    
    // LIKE 검색은 성능에 주의
    @SearchableField(operators = {CONTAINS, STARTS_WITH})
    private String title;  // Full-text 인덱스 고려
}
```

### 쿼리 최적화

```java
@Service
public class OptimizedPostService extends DefaultSearchableService<Post> {
    
    // 자주 사용되는 검색 조건을 미리 정의
    private static final SearchCondition<PostSearchDTO> PUBLISHED_POSTS_CONDITION = 
        SearchConditionBuilder.create(PostSearchDTO.class)
            .where(group -> group.equals("status", PostStatus.PUBLISHED))
            .sort(sort -> sort.desc("createdAt"))
            .build();
    
    public Page<Post> findRecentPublishedPosts(int page, int size) {
        // 미리 정의된 조건 복사 후 페이징 설정
        SearchCondition<PostSearchDTO> condition = copyCondition(PUBLISHED_POSTS_CONDITION);
        condition.setPage(page);
        condition.setSize(size);
        
        return findAllWithSearch(condition);
    }
}
```

## 에러 처리

### 커스텀 예외 처리

```java
@Service
public class PostService extends DefaultSearchableService<Post> {
    
    public Page<Post> safeSearch(SearchCondition<PostSearchDTO> condition) {
        try {
            return findAllWithSearch(condition);
        } catch (SearchableValidationException e) {
            log.warn("검색 조건 검증 실패: {}", e.getMessage());
            // 기본 조건으로 대체
            return findAllWithSearch(getDefaultCondition());
        } catch (SearchableException e) {
            log.error("검색 실행 중 오류 발생", e);
            throw new ServiceException("검색을 수행할 수 없습니다.", e);
        }
    }
    
    private SearchCondition<PostSearchDTO> getDefaultCondition() {
        return SearchConditionBuilder.create(PostSearchDTO.class)
            .where(group -> group.equals("status", PostStatus.PUBLISHED))
            .sort(sort -> sort.desc("createdAt"))
            .page(0)
            .size(10)
            .build();
    }
}
```

## 테스트

### 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class PostServiceTest {
    
    @Mock
    private PostRepository postRepository;
    
    @InjectMocks
    private PostService postService;
    
    @Test
    void testSearchWithBuilder() {
        // given
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("status", PostStatus.PUBLISHED)
                .contains("title", "Spring")
            )
            .build();
            
        // when
        Page<Post> result = postService.findAllWithSearch(condition);
        
        // then
        assertThat(result).isNotNull();
        verify(postRepository).findAll(any(Specification.class), any(Pageable.class));
    }
}
```

### 통합 테스트

```java
@SpringBootTest
@Transactional
class PostSearchIntegrationTest {
    
    @Autowired
    private PostService postService;
    
    @Test
    void testComplexSearch() {
        // given
        createTestData();
        
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .contains("title", "Spring")
                .greaterThan("viewCount", 100L)
            )
            .or(group -> group
                .equals("authorName", "John")
            )
            .sort(sort -> sort.desc("createdAt"))
            .page(0)
            .size(10)
            .build();
            
        // when
        Page<Post> result = postService.findAllWithSearch(condition);
        
        // then
        assertThat(result.getContent()).hasSize(3);
        assertThat(result.getContent().get(0).getCreatedAt())
            .isAfter(result.getContent().get(1).getCreatedAt());
    }
}
```

## 다음 단계

- [커서 페이징](cursor-pagination.md) - 대용량 데이터 처리를 위한 커서 기반 페이징
- [OpenAPI 통합](openapi-integration.md) - Swagger 문서 자동 생성
- [API 레퍼런스](api-reference.md) - 전체 API 문서

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 검색 연산자](search-operators.md) | [다음: 커서 페이징](cursor-pagination.md) 