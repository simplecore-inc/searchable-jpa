# 고급 기능

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: 2단계 쿼리 최적화](two-phase-query-optimization.md) | [다음: 관계형 데이터와 2단계 쿼리](relationship-and-two-phase-query.md)

---

이 문서는 Searchable JPA의 고급 기능들을 설명합니다.

## 프로젝션(Projection) 지원

엔티티의 일부 필드만 조회하고 싶을 때 프로젝션을 사용할 수 있습니다.

### 인터페이스 기반 프로젝션

```java
public interface PostSummary {
    String getTitle();
    String getAuthorName();
    LocalDateTime getCreatedAt();
    
    // 계산된 필드
    @Value("#{target.title + ' by ' + target.authorName}")
    String getDisplayName();
}
```

### 클래스 기반 프로젝션

```java
public class PostSummaryDTO {
    private final String title;
    private final String authorName;
    private final LocalDateTime createdAt;
    
    public PostSummaryDTO(String title, String authorName, LocalDateTime createdAt) {
        this.title = title;
        this.authorName = authorName;
        this.createdAt = createdAt;
    }
    
    // getters
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

@GetMapping("/dto-summaries")
public Page<PostSummaryDTO> getPostSummariesAsDTO(
    @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
) {
    SearchCondition<PostSearchDTO> condition = 
        new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
    return postService.findAllWithSearch(condition, PostSummaryDTO.class);
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

## 커스텀 연산자

기본 제공되는 연산자 외에 커스텀 연산자를 정의할 수 있습니다.

### 커스텀 연산자 정의

```java
public enum CustomSearchOperator implements SearchOperator {
    FULL_TEXT_SEARCH("fullTextSearch") {
        @Override
        public <T> Predicate toPredicate(Root<T> root, CriteriaBuilder cb, String field, Object value) {
            // 전문 검색 로직 구현
            return cb.function("MATCH", Boolean.class, 
                root.get(field), cb.literal(value)).isTrue();
        }
    },
    
    REGEX_MATCH("regexMatch") {
        @Override
        public <T> Predicate toPredicate(Root<T> root, CriteriaBuilder cb, String field, Object value) {
            // 정규식 매칭 로직
            return cb.function("REGEXP", Boolean.class, 
                root.get(field), cb.literal(value)).isTrue();
        }
    },
    
    DISTANCE_WITHIN("distanceWithin") {
        @Override
        public <T> Predicate toPredicate(Root<T> root, CriteriaBuilder cb, String field, Object value) {
            // 지리적 거리 검색 로직
            String[] parts = value.toString().split(",");
            double lat = Double.parseDouble(parts[0]);
            double lon = Double.parseDouble(parts[1]);
            double distance = Double.parseDouble(parts[2]);
            
            return cb.lessThan(
                cb.function("ST_Distance", Double.class, 
                    root.get(field), 
                    cb.function("ST_Point", Object.class, cb.literal(lat), cb.literal(lon))),
                distance
            );
        }
    };
    
    private final String operation;
    
    CustomSearchOperator(String operation) {
        this.operation = operation;
    }
    
    @Override
    public String getOperation() {
        return operation;
    }
}
```

### 커스텀 연산자 사용

```java
public class PostSearchDTO {
    @SearchableField(operators = {CustomSearchOperator.FULL_TEXT_SEARCH})
    private String content;
    
    @SearchableField(operators = {CustomSearchOperator.REGEX_MATCH})
    private String title;
    
    @SearchableField(operators = {CustomSearchOperator.DISTANCE_WITHIN})
    private String location;
}
```

### 커스텀 연산자 검색 예제

```bash
# 전문 검색
GET /api/posts/search?content.fullTextSearch=spring boot tutorial

# 정규식 검색
GET /api/posts/search?title.regexMatch=^Tutorial.*

# 지리적 거리 검색 (위도,경도,거리(km))
GET /api/posts/search?location.distanceWithin=37.5665,126.9780,10
```

## 다국어 지원

검색 조건의 메시지를 다국어로 제공할 수 있습니다.

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

# messages_ja.properties
search.error.invalid.field=無効なフィールド: {0}
search.error.invalid.operator=サポートされていない演算子: {0}
search.error.invalid.value=無効な値: {0}
```

### 다국어 메시지 설정

```java
@Configuration
public class MessageConfig {
    
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("messages/search");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        return messageSource;
    }
    
    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        localeResolver.setDefaultLocale(Locale.KOREAN);
        localeResolver.setSupportedLocales(Arrays.asList(
            Locale.KOREAN, Locale.ENGLISH, Locale.JAPANESE
        ));
        return localeResolver;
    }
}
```

### 다국어 에러 처리

```java
@ControllerAdvice
public class SearchExceptionHandler {
    
    @Autowired
    private MessageSource messageSource;
    
    @ExceptionHandler(SearchableValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
        SearchableValidationException ex, 
        HttpServletRequest request
    ) {
        Locale locale = RequestContextUtils.getLocale(request);
        String message = messageSource.getMessage(
            ex.getMessageKey(), 
            ex.getArguments(), 
            ex.getDefaultMessage(), 
            locale
        );
        
        ErrorResponse error = new ErrorResponse(
            "VALIDATION_ERROR", 
            message, 
            ex.getField()
        );
        
        return ResponseEntity.badRequest().body(error);
    }
}
```

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