# 고급 기능

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 2단계 쿼리 최적화](two-phase-query-optimization.md) | [다음: 관계형 데이터와 2단계 쿼리](relationship-and-two-phase-query.md)

---

이 문서는 Searchable JPA의 고급 기능들을 설명합니다.

## 프로젝션(Projection) 지원

엔티티의 일부 필드만 조회하고 싶을 때 인터페이스 기반 프로젝션을 사용할 수 있습니다.

### 인터페이스 기반 프로젝션

```java
public interface PostSummary {
    String getTitle();
    String getAuthorName();
    LocalDateTime getCreatedAt();

    // 계산된 필드 (SpEL 표현식 지원)
    @Value("#{target.title + ' by ' + target.authorName}")
    String getDisplayName();
}
```

### 프로젝션 사용

```java
@GetMapping("/summaries")
public Page<PostSummary> getPostSummaries(
    @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
) {
    SearchCondition<PostSearchDTO> condition =
        new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
    return postService.findAllWithSearch(condition, PostSummary.class);
}
```

### 동적 프로젝션

```java
@GetMapping("/dynamic-summaries")
public Page<?> getDynamicSummaries(
    @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params,
    @RequestParam(defaultValue = "summary") String projection
) {
    SearchCondition<PostSearchDTO> condition =
        new SearchableParamsParser<>(PostSearchDTO.class).convert(params);

    switch (projection) {
        case "summary":
            return postService.findAllWithSearch(condition, PostSummary.class);
        case "detail":
            return postService.findAllWithSearch(condition, PostDetailProjection.class);
        default:
            return postService.findAllWithSearch(condition);
    }
}
```

### 제한사항

- **인터페이스만 지원**: 현재 구현에서는 인터페이스 기반 프로젝션만 지원됩니다
- **클래스 기반 프로젝션**: DTO 클래스를 사용한 프로젝션은 아직 지원되지 않습니다
- **계산된 필드**: `@Value` 어노테이션을 사용한 SpEL 표현식을 지원합니다

## 배치 업데이트

검색 조건에 맞는 여러 엔티티를 한 번에 업데이트할 수 있습니다.

### 업데이트 DTO

```java
public class PostUpdateDTO {
    private PostStatus status;
    private String title;
    private Integer viewCount;
    private LocalDateTime lastModified;
    
    // getters and setters
}
```

### 배치 업데이트 실행

```java
@PutMapping("/batch-update")
public ResponseEntity<Long> batchUpdate(
    @RequestBody SearchCondition<PostSearchDTO> searchCondition,
    @RequestBody PostUpdateDTO updateData
) {
    long updatedCount = postService.updateWithSearch(searchCondition, updateData);
    return ResponseEntity.ok(updatedCount);
}
```

### 조건부 배치 업데이트

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    @Transactional
    public long updatePostStatus(PostStatus fromStatus, PostStatus toStatus) {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group.equals("status", fromStatus))
            .build();
            
        PostUpdateDTO updateData = new PostUpdateDTO();
        updateData.setStatus(toStatus);
        updateData.setLastModified(LocalDateTime.now());
        
        return updateWithSearch(condition, updateData);
    }
}
```

### 사용 예제

```bash
# 특정 조건의 게시글 상태를 일괄 변경
POST /api/posts/batch-update
Content-Type: application/json

{
  "searchCondition": {
    "nodes": [
      {
        "field": "status",
        "searchOperator": "equals",
        "value": "DRAFT"
      },
      {
        "field": "createdAt",
        "searchOperator": "lessThan",
        "value": "2024-01-01T00:00:00"
      }
    ]
  },
  "updateData": {
    "status": "ARCHIVED",
    "lastModified": "2024-01-15T10:30:00"
  }
}
```

## 배치 삭제

검색 조건에 맞는 여러 엔티티를 한 번에 삭제할 수 있습니다.

### 기본 배치 삭제

```java
@DeleteMapping("/batch-delete")
public ResponseEntity<Long> batchDelete(
    @RequestBody SearchCondition<PostSearchDTO> searchCondition
) {
    long deletedCount = postService.deleteWithSearch(searchCondition);
    return ResponseEntity.ok(deletedCount);
}
```

### 안전한 배치 삭제

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    @Transactional
    public long safeDeleteOldDrafts(int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(group -> group
                .equals("status", PostStatus.DRAFT)
                .and(a -> a.lessThan("createdAt", cutoffDate))
            )
            .build();
            
        // 삭제 전 개수 확인
        Page<Post> toDelete = findAllWithSearch(condition);
        log.info("Deleting {} old draft posts", toDelete.getTotalElements());
        
        return deleteWithSearch(condition);
    }
}
```

## 동적 정렬

검색 조건과 함께 동적으로 정렬 조건을 지정할 수 있습니다.

### 다중 필드 정렬

```bash
# 상태 오름차순, 생성일 내림차순, ID 오름차순
GET /api/posts/search?sort=status.asc,createdAt.desc,id.asc
```

### JSON 방식 동적 정렬

```json
{
  "nodes": [
    {
      "field": "status",
      "searchOperator": "equals",
      "value": "PUBLISHED"
    }
  ],
  "sort": {
    "orders": [
      {
        "field": "priority",
        "direction": "DESC"
      },
      {
        "field": "createdAt",
        "direction": "ASC"
      },
      {
        "field": "id",
        "direction": "ASC"
      }
    ]
  }
}
```

### 프로그래매틱 정렬

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    public Page<Post> findPostsWithDynamicSort(String status, String sortField, String sortDirection) {
        SearchConditionBuilder<PostSearchDTO> builder = SearchConditionBuilder
            .create(PostSearchDTO.class);
            
        if (status != null) {
            builder = builder.where(group -> group.equals("status", PostStatus.valueOf(status)));
        }
        
        // 동적 정렬 추가
        if (sortField != null && sortDirection != null) {
            builder = builder.sort(sort -> {
                if ("ASC".equalsIgnoreCase(sortDirection)) {
                    return sort.asc(sortField);
                } else {
                    return sort.desc(sortField);
                }
            });
        }
        
        SearchCondition<PostSearchDTO> condition = builder.build();
        return findAllWithSearch(condition);
    }
}
```

## 중첩 필드 검색

연관 엔티티의 필드로 검색할 수 있습니다.

### 깊은 중첩 필드 검색

```java
public class PostSearchDTO {
    // 2단계 중첩
    @SearchableField(entityField = "author.profile.department", operators = {EQUALS, CONTAINS})
    private String authorDepartment;
    
    // 3단계 중첩
    @SearchableField(entityField = "author.profile.company.name", operators = {CONTAINS})
    private String companyName;
    
    // 컬렉션 중첩
    @SearchableField(entityField = "comments.author.name", operators = {CONTAINS})
    private String commentAuthorName;
}
```

### 중첩 필드 조건부 검색

```java
@GetMapping("/advanced-search")
public Page<Post> advancedSearch(
    @RequestParam(required = false) String authorDepartment,
    @RequestParam(required = false) String companyName,
    @RequestParam(required = false) String commentAuthorName
) {
    SearchConditionBuilder<PostSearchDTO> builder = SearchConditionBuilder
        .create(PostSearchDTO.class);
        
    if (authorDepartment != null) {
        builder = builder.where(group -> group.contains("authorDepartment", authorDepartment));
    }
    
    if (companyName != null) {
        builder = builder.and(group -> group.contains("companyName", companyName));
    }
    
    if (commentAuthorName != null) {
        builder = builder.and(group -> group.contains("commentAuthorName", commentAuthorName));
    }
    
    SearchCondition<PostSearchDTO> condition = builder.build();
    return postService.findAllWithSearch(condition);
}
```



## 기존 검색 조건 확장

기존 `SearchCondition` 객체를 기반으로 새로운 조건을 추가할 수 있습니다. 이 기능은 불변성을 유지하면서 검색 조건을 재사용하고 확장하는 데 유용합니다.

### from() 팩토리 메서드

`SearchConditionBuilder.from()` 메서드를 사용하여 기존 검색 조건을 복사하고 새 조건을 추가할 수 있습니다.

```java
// 기본 검색 조건 생성
SearchCondition<PostSearchDTO> baseCondition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(w -> w.equals("status", PostStatus.PUBLISHED))
    .sort(s -> s.desc("createdAt"))
    .page(0)
    .size(10)
    .build();

// 기존 조건 기반으로 새 조건 추가
SearchCondition<PostSearchDTO> extendedCondition = SearchConditionBuilder
    .from(baseCondition, PostSearchDTO.class)
    .and(a -> a.greaterThan("viewCount", 100))
    .build();

// baseCondition은 변경되지 않음 (불변성 유지)
```

### 실용적인 활용 사례

#### 1. 테넌트별 필터 추가

멀티테넌트 환경에서 기본 검색 조건에 테넌트 필터를 추가합니다.

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {

    public Page<Post> searchWithTenantFilter(
            SearchCondition<PostSearchDTO> baseCondition,
            Long tenantId
    ) {
        SearchCondition<PostSearchDTO> tenantCondition = SearchConditionBuilder
            .from(baseCondition, PostSearchDTO.class)
            .and(a -> a.equals("tenantId", tenantId))
            .build();

        return findAllWithSearch(tenantCondition);
    }
}
```

#### 2. 권한 기반 필터 추가

사용자 권한에 따라 검색 조건을 동적으로 확장합니다.

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {

    public Page<Post> searchWithSecurityFilter(
            SearchCondition<PostSearchDTO> baseCondition,
            User currentUser
    ) {
        SearchConditionBuilder<PostSearchDTO> builder = SearchConditionBuilder
            .from(baseCondition, PostSearchDTO.class);

        // 관리자가 아닌 경우 자신의 게시글만 조회
        if (!currentUser.isAdmin()) {
            builder = builder.and(a -> a.equals("authorId", currentUser.getId()));
        }

        // 비공개 게시글 제외 (소유자가 아닌 경우)
        if (!currentUser.isAdmin()) {
            builder = builder.and(a -> a
                .notEquals("visibility", "PRIVATE")
                .or(o -> o.equals("authorId", currentUser.getId()))
            );
        }

        return findAllWithSearch(builder.build());
    }
}
```

#### 3. 컨트롤러에서 조건 확장

클라이언트 요청에 서버 측 조건을 추가합니다.

```java
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    @PostMapping("/search")
    public Page<Post> search(
            @RequestBody SearchCondition<PostSearchDTO> clientCondition,
            @AuthenticationPrincipal User currentUser
    ) {
        // 클라이언트 조건에 서버 측 필터 추가
        SearchCondition<PostSearchDTO> serverCondition = SearchConditionBuilder
            .from(clientCondition, PostSearchDTO.class)
            .and(a -> a.notEquals("status", PostStatus.DELETED))
            .and(a -> a.in("departmentId", currentUser.getAccessibleDepartments()))
            .build();

        return postService.findAllWithSearch(serverCondition);
    }
}
```

### 정렬 및 페이징 오버라이드

기존 조건의 정렬이나 페이징을 변경할 수 있습니다.

```java
// 기존 조건
SearchCondition<PostSearchDTO> original = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(w -> w.equals("status", PostStatus.PUBLISHED))
    .sort(s -> s.asc("title"))
    .page(0)
    .size(10)
    .build();

// 정렬 변경
SearchCondition<PostSearchDTO> withNewSort = SearchConditionBuilder
    .from(original, PostSearchDTO.class)
    .sort(s -> s.desc("createdAt"))  // 정렬 오버라이드
    .build();

// 페이징 변경
SearchCondition<PostSearchDTO> withNewPage = SearchConditionBuilder
    .from(original, PostSearchDTO.class)
    .page(5)   // 페이지 오버라이드
    .size(20)  // 사이즈 오버라이드
    .build();
```

### 주의사항

- **불변성**: `from()` 메서드는 항상 새로운 `SearchCondition` 객체를 생성합니다. 원본 객체는 변경되지 않습니다.
- **DTO 클래스 필수**: `from()` 메서드에 DTO 클래스를 명시적으로 전달해야 합니다. 이는 `build()` 시점에 검증을 수행하기 위함입니다.
- **검증 시점**: 모든 조건(기존 + 새로 추가된 조건)은 `build()` 호출 시 함께 검증됩니다.

## 다국어 지원

검색 조건의 에러 메시지를 다국어로 제공할 수 있습니다.

### 메시지 파일 설정

```properties
# messages_ko.properties
search.error.invalid.field=잘못된 필드입니다: {0}
search.error.invalid.operator=지원하지 않는 연산자입니다: {0}
search.error.invalid.value=잘못된 값입니다: {0}
search.validation.required=필수 항목입니다: {0}
search.validation.pattern=형식이 올바르지 않습니다: {0}

# messages_en.properties
search.error.invalid.field=Invalid field: {0}
search.error.invalid.operator=Unsupported operator: {0}
search.error.invalid.value=Invalid value: {0}
search.validation.required=Required field: {0}
search.validation.pattern=Invalid format: {0}
```

### MessageUtils 사용

```java
import dev.simplecore.searchable.core.i18n.MessageUtils;

@Service
public class PostService extends DefaultSearchableService<Post, Long> {

    public void validateAndSave(Post post) {
        if (post.getTitle() == null || post.getTitle().trim().isEmpty()) {
            throw new IllegalArgumentException(
                MessageUtils.getMessage("search.validation.required", new Object[]{"title"})
            );
        }

        if (post.getStatus() == null) {
            throw new IllegalArgumentException(
                MessageUtils.getMessage("search.error.invalid.value", new Object[]{"status"})
            );
        }

        save(post);
    }
}
```

### Spring Boot 자동 구성

MessageUtils는 Spring Boot의 자동 구성 기능을 통해 자동으로 초기화됩니다. 별도의 설정 없이 사용할 수 있습니다.

## 복합 키 지원

복합 키 엔티티에 대한 자세한 내용은 다음 문서를 참조하세요:

- [2단계 쿼리 최적화 - 복합 키 지원](two-phase-query-optimization.md#복합-키-지원)
- [설치 가이드 - 복합 키 엔티티 설정](installation.md#복합-키-엔티티-설정)

### 간단한 사용 예제

```java
// @IdClass 방식
@Service
public class IdClassService extends DefaultSearchableService<TestIdClassEntity, TestIdClassEntity.CompositeKey> {
    // 자동으로 복합 키 최적화 적용
}

// @EmbeddedId 방식  
@Service
public class EmbeddedIdService extends DefaultSearchableService<TestCompositeKeyEntity, TestCompositeKeyEntity.CompositeKey> {
    // 자동으로 복합 키 최적화 적용
}
```

이러한 고급 기능들을 통해 복잡한 검색 요구사항도 효율적으로 처리할 수 있습니다.

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 2단계 쿼리 최적화](two-phase-query-optimization.md) | [다음: 관계형 데이터와 2단계 쿼리](relationship-and-two-phase-query.md) 