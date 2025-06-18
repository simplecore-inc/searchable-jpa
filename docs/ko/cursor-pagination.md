# 커서 기반 페이징 (Cursor-based Pagination)

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 고급 기능](advanced-features.md) | [다음: OpenAPI 통합](openapi-integration.md)

---

Searchable JPA는 대용량 데이터에서 높은 성능을 제공하는 커서 기반 페이징을 지원합니다. 기존의 OFFSET 기반 페이징의 성능 문제를 해결하고, 실시간 데이터 변경에도 일관된 결과를 제공합니다.

## 커서 페이징의 장점

### 1. 성능 향상
- **OFFSET 방식**: 깊은 페이지로 갈수록 성능이 급격히 저하 (O(n))
- **커서 방식**: 모든 페이지에서 일정한 성능 유지 (O(log n))

### 2. 데이터 일관성
- 페이징 중 데이터가 추가/삭제되어도 중복이나 누락 없음
- 실시간 피드나 타임라인에 적합

## 기본 사용법

### 1. 서비스 계층 구현

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
    
    // 기본 findAllWithSearch 메서드가 내부적으로 커서 기반 페이징을 사용
    public Page<Post> findPosts(SearchCondition<PostSearchDTO> condition) {
        return findAllWithSearch(condition);
    }
    
    // 크기 제한과 함께 사용
    public Page<Post> findPostsWithLimit(SearchCondition<PostSearchDTO> condition, 
                                        int maxSize) {
        // 서비스 레벨에서 크기 제한 적용
        if (condition.getSize() > maxSize) {
            condition = SearchConditionBuilder.from(condition)
                .size(maxSize)
                .build();
        }
        return findAllWithSearch(condition);
    }
}
```

### 2. 컨트롤러에서 사용

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {
    
    private final PostService postService;
    
    @GetMapping("/search")
    public Page<Post> searchPosts(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
    ) {
        SearchCondition<PostSearchDTO> condition = 
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return postService.findPosts(condition);
    }
    
    // 페이지 크기 제한을 적용한 검색
    @GetMapping("/search-limited")
    public Page<Post> searchPostsLimited(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params,
        @RequestParam(defaultValue = "20") int size
    ) {
        SearchCondition<PostSearchDTO> condition = 
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        
        // 크기 설정
        condition = SearchConditionBuilder.from(condition)
            .size(Math.min(size, 100)) // 최대 100개로 제한
            .build();
            
        return postService.findPosts(condition);
    }
}
```

## 페이징 응답 구조

표준 Spring Data의 `Page` 객체를 사용하지만, 내부적으로는 커서 기반으로 처리됩니다:

```java
public interface Page<T> {
    List<T> getContent();           // 현재 페이지 데이터
    int getNumber();                // 현재 페이지 번호 (0부터 시작)
    int getSize();                  // 페이지 크기
    int getTotalPages();            // 전체 페이지 수
    long getTotalElements();        // 전체 요소 수
    boolean hasNext();              // 다음 페이지 존재 여부
    boolean hasPrevious();          // 이전 페이지 존재 여부
    boolean isFirst();              // 첫 페이지 여부
    boolean isLast();               // 마지막 페이지 여부
    int getNumberOfElements();      // 현재 페이지 요소 수
}
```

### 응답 예제

```json
{
  "content": [
    {
      "id": 100,
      "title": "Spring Boot Tutorial",
      "createdAt": "2024-01-15T10:30:00",
      "viewCount": 1500
    },
    {
      "id": 99,
      "title": "JPA Best Practices",
      "createdAt": "2024-01-14T15:20:00",
      "viewCount": 1200
    }
  ],
  "pageable": {
    "sort": {
      "sorted": true,
      "orders": [
        {
          "property": "createdAt",
          "direction": "DESC"
        }
      ]
    },
    "pageNumber": 0,
    "pageSize": 20
  },
  "totalElements": 1000,
  "totalPages": 50,
  "number": 0,
  "size": 20,
  "numberOfElements": 2,
  "first": true,
  "last": false,
  "hasNext": true,
  "hasPrevious": false
}
```

## 페이징 사용 패턴

### 1. 첫 페이지 요청

```bash
# 첫 페이지 (페이지 번호 0 또는 생략)
GET /api/posts/search?size=10&sort=createdAt,desc
GET /api/posts/search?page=0&size=10&sort=createdAt,desc
```

### 2. 다음 페이지 요청

```bash
# 페이지 번호를 증가시켜 다음 페이지 요청
GET /api/posts/search?page=1&size=10&sort=createdAt,desc
GET /api/posts/search?page=2&size=10&sort=createdAt,desc
```

### 3. 이전 페이지 요청

```bash
# 페이지 번호를 감소시켜 이전 페이지 요청
GET /api/posts/search?page=0&size=10&sort=createdAt,desc
```

## 정렬 기준과 커서

### 단일 필드 정렬

```java
// 생성일 기준 내림차순 정렬
@SearchableField(sortable = true)
private LocalDateTime createdAt;

// 사용 예제
GET /api/posts/search?sort=createdAt,desc&size=10
```

### 다중 필드 정렬

```java
// 여러 필드 조합 정렬
@SearchableField(sortable = true)
private PostStatus status;

@SearchableField(sortable = true)
private LocalDateTime createdAt;

@SearchableField(sortable = true)
private Long id;

// 사용 예제 - 상태별, 생성일별, ID별 정렬
GET /api/posts/search?sort=status,asc&sort=createdAt,desc&sort=id,desc&size=10
```

### 고유성 보장

커서 페이징에서는 정렬 기준이 고유해야 합니다. 고유하지 않은 경우 ID를 추가로 정렬 기준에 포함시켜야 합니다.

```java
// 잘못된 예 - viewCount만으로는 고유성 보장 안됨
GET /api/posts/search?sort=viewCount,desc

// 올바른 예 - ID를 추가하여 고유성 보장
GET /api/posts/search?sort=viewCount,desc&sort=id,desc
```

## 프로그래매틱 사용

### SearchConditionBuilder와 함께 사용

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    public Page<Post> findRecentPosts(int page, int size) {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("status", PostStatus.PUBLISHED)
            )
            .sort(sort -> sort
                .desc("createdAt")
                .desc("id")  // 고유성 보장을 위한 ID 추가
            )
            .page(page)
            .size(size)
            .build();
            
        return findAllWithSearch(condition);
    }
}
```

### 조건부 페이징

```java
public Page<Post> searchPosts(String title, 
                             PostStatus status, 
                             int page,
                             int size) {
    ChainedSearchCondition<PostSearchDTO> builder = SearchConditionBuilder
        .create(PostSearchDTO.class);
        
    if (title != null && !title.isEmpty()) {
        builder = builder.where(group -> group.contains("title", title));
    }
    
    if (status != null) {
        builder = builder.where(group -> group.equals("status", status));
    }
    
    SearchCondition<PostSearchDTO> condition = builder
        .sort(sort -> sort.desc("createdAt").desc("id"))
        .page(page)
        .size(size)
        .build();
        
    return findAllWithSearch(condition);
}
```

## 성능 최적화

### 1. 적절한 인덱스 설정

```sql
-- 단일 필드 정렬용 인덱스
CREATE INDEX idx_posts_created_at_id ON posts(created_at DESC, id DESC);

-- 다중 필드 정렬용 복합 인덱스
CREATE INDEX idx_posts_status_created_at_id ON posts(status ASC, created_at DESC, id DESC);

-- 검색 조건 + 정렬용 복합 인덱스
CREATE INDEX idx_posts_status_title_created_at_id ON posts(status, title, created_at DESC, id DESC);
```

### 2. 페이지 크기 제한

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;
    
    public Page<Post> findPostsWithValidation(SearchCondition<PostSearchDTO> condition) {
        // 크기 제한 적용
        int requestedSize = condition.getSize();
        int validatedSize = requestedSize > 0 ? Math.min(requestedSize, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        
        if (validatedSize != requestedSize) {
            condition = SearchConditionBuilder.from(condition)
                .size(validatedSize)
                .build();
        }
        
        return findAllWithSearch(condition);
    }
}
```

### 3. 캐싱 전략

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    @Cacheable(value = "posts", key = "#condition.hashCode() + '_' + #condition.page + '_' + #condition.size")
    public Page<Post> findPostsWithCache(SearchCondition<PostSearchDTO> condition) {
        return findAllWithSearch(condition);
    }
    
    @CacheEvict(value = "posts", allEntries = true)
    public Post createPost(Post post) {
        return repository.save(post);
    }
}
```

## 내부 구현 방식

Searchable JPA의 커서 페이징은 다음과 같이 동작합니다:

### 1. 자동 커서 변환

```java
// 사용자 요청: page=5, size=20
// 내부적으로 커서 기반 쿼리로 변환
// 1. 5 * 20 = 100번째 레코드의 커서 값 계산
// 2. 커서 조건을 WHERE 절에 추가
// 3. LIMIT 20으로 데이터 조회
```

### 2. 커서 조건 생성

```sql
-- 기존 OFFSET 방식 (성능 문제)
SELECT * FROM posts 
WHERE status = 'PUBLISHED' 
ORDER BY created_at DESC, id DESC 
LIMIT 20 OFFSET 100;

-- 커서 방식 (고성능)
SELECT * FROM posts 
WHERE status = 'PUBLISHED' 
  AND (created_at < '2024-01-15 10:30:00' 
       OR (created_at = '2024-01-15 10:30:00' AND id < 100))
ORDER BY created_at DESC, id DESC 
LIMIT 20;
```

### 3. API 호환성 유지

```java
// 클라이언트는 여전히 페이지 번호를 사용
Page<Post> result = postService.findAllWithSearch(condition);

// 하지만 내부적으로는 커서 기반으로 처리
// 클라이언트는 변경 사항을 인지할 필요 없음
```

## 클라이언트 구현 예제

### JavaScript 클라이언트

```javascript
class PostPagination {
    constructor(apiEndpoint) {
        this.apiEndpoint = apiEndpoint;
        this.currentPage = 0;
        this.pageSize = 20;
    }
    
    async loadPage(page = 0, searchParams = {}) {
        const params = new URLSearchParams({
            ...searchParams,
            page: page.toString(),
            size: this.pageSize.toString()
        });
        
        const response = await fetch(`${this.apiEndpoint}?${params}`);
        const data = await response.json();
        
        this.currentPage = data.number;
        
        return data;
    }
    
    async loadNextPage(searchParams = {}) {
        return this.loadPage(this.currentPage + 1, searchParams);
    }
    
    async loadPreviousPage(searchParams = {}) {
        if (this.currentPage > 0) {
            return this.loadPage(this.currentPage - 1, searchParams);
        }
        throw new Error('Already at first page');
    }
    
    async loadFirstPage(searchParams = {}) {
        return this.loadPage(0, searchParams);
    }
}

// 사용 예제
const pagination = new PostPagination('/api/posts/search');

// 첫 페이지 로드
const firstPage = await pagination.loadFirstPage({ 'title.contains': 'Spring' });

// 다음 페이지 로드
if (firstPage.hasNext) {
    const nextPage = await pagination.loadNextPage({ 'title.contains': 'Spring' });
}
```

## 제한사항과 고려사항

### 제한사항

1. **페이지 번호 없음**: 특정 페이지로 바로 이동할 수 없습니다
2. **총 개수 미제공**: 전체 데이터 개수를 알 수 없습니다
3. **정렬 기준 고정**: 페이징 중 정렬 기준을 변경할 수 없습니다

### 사용 시 고려사항

1. **정렬 필드 인덱스**: 정렬에 사용되는 모든 필드에 적절한 인덱스를 설정하세요
2. **고유성 보장**: 정렬 기준이 고유하지 않으면 ID를 추가로 포함시키세요
3. **커서 만료**: 오래된 커서는 데이터 변경으로 인해 부정확할 수 있습니다

## 일반 페이징과 비교

| 특성 | 일반 페이징 | 커서 페이징 |
|------|-------------|-------------|
| 성능 | 페이지 번호가 클수록 느림 | 일관된 성능 |
| 메모리 사용량 | 페이지 번호에 비례 | 일정함 |
| 특정 페이지 이동 | 가능 | 불가능 |
| 전체 개수 | 제공 | 미제공 |
| 실시간 데이터 | 중복/누락 가능 | 일관성 보장 |
| 구현 복잡도 | 간단 | 중간 |

## 다음 단계

- [OpenAPI 통합](openapi-integration.md) - Swagger 문서 자동 생성
- [API 레퍼런스](api-reference.md) - 전체 API 문서

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 고급 기능](advanced-features.md) | [다음: OpenAPI 통합](openapi-integration.md) 