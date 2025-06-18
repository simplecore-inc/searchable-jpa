# 자주 묻는 질문 (FAQ)

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: API 레퍼런스](api-reference.md)

---

## 설치 및 설정

### Q: Spring Boot 버전 호환성은 어떻게 되나요?

**A:** Searchable JPA는 Spring Boot 2.7.x 이상을 지원합니다. 주요 호환성 정보:

- Spring Boot 2.7.x: 완전 지원
- Spring Boot 3.x: 현재 개발 중 (향후 버전에서 지원 예정)
- Java 8+: 지원
- JPA 2.2+: 지원

### Q: 기존 프로젝트에 추가할 때 기존 코드에 영향을 주나요?

**A:** 아니요. Searchable JPA는 기존 JPA Repository와 완전히 독립적으로 동작합니다. 기존 코드를 수정할 필요 없이 새로운 검색 기능만 추가할 수 있습니다.

```java
// 기존 Repository는 그대로 유지
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    List<Post> findByTitle(String title); // 기존 메서드 유지
}

// Searchable 기능만 추가
@Service  
public class PostService extends DefaultSearchableService<Post> {
    // 새로운 검색 기능
}
```

### Q: Maven을 사용하는데 의존성 설정은 어떻게 하나요?

**A:** Maven 사용 시 다음과 같이 설정하세요:

```xml
<dependency>
    <groupId>dev.simplecore.searchable</groupId>
    <artifactId>spring-boot-starter-searchable-jpa</artifactId>
    <version>0.0.4-SNAPSHOT</version>
</dependency>
```

## 기본 사용법

### Q: DTO 필드명과 엔티티 필드명이 다를 때 어떻게 매핑하나요?

**A:** `@SearchableField`의 `entityField` 속성을 사용하세요:

```java
public class PostSearchDTO {
    @SearchableField(entityField = "title", operators = {CONTAINS})
    private String searchTitle; // DTO 필드명
    
    @SearchableField(entityField = "author.name", operators = {CONTAINS})
    private String authorName; // 중첩 필드 매핑
}
```

### Q: 모든 필드에 @SearchableField를 붙여야 하나요?

**A:** 아니요. 검색 가능하게 만들고 싶은 필드에만 붙이면 됩니다. 어노테이션이 없는 필드는 검색 조건에서 무시됩니다.

### Q: 날짜 검색 시 어떤 형식을 사용해야 하나요?

**A:** ISO 8601 형식을 사용하세요:

```bash
# LocalDateTime
GET /api/posts/search?createdAt.greaterThan=2024-01-01T00:00:00

# LocalDate  
GET /api/posts/search?publishDate.equals=2024-01-01

# 범위 검색
GET /api/posts/search?createdAt.between=2024-01-01T00:00:00,2024-12-31T23:59:59
```

## 검색 연산자

### Q: 대소문자 구분 없이 검색하려면 어떻게 해야 하나요?

**A:** 현재 버전에서는 대소문자 구분 없는 검색을 위해 데이터베이스 함수를 활용하거나, 검색 전에 값을 소문자로 변환하는 방법을 사용할 수 있습니다:

```java
// 서비스에서 전처리
public Page<Post> searchPosts(String title) {
    SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
        .create(PostSearchDTO.class)
        .where(group -> group.contains("title", title.toLowerCase()))
        .build();
    return findAllWithSearch(condition);
}
```

### Q: LIKE 검색에서 와일드카드를 직접 사용할 수 있나요?

**A:** 아니요. 보안상의 이유로 와일드카드는 자동으로 추가됩니다. 대신 적절한 연산자를 사용하세요:

```java
// 자동으로 %가 추가됨
title.contains=Spring     // WHERE title LIKE '%Spring%'
title.startsWith=Spring   // WHERE title LIKE 'Spring%'  
title.endsWith=Boot       // WHERE title LIKE '%Boot'
```

### Q: IN 연산자에서 사용할 수 있는 값의 개수에 제한이 있나요?

**A:** 기술적 제한은 없지만, 데이터베이스 성능을 위해 1000개 이하로 제한하는 것을 권장합니다. 많은 값이 필요한 경우 다른 검색 방식을 고려하세요.

## 고급 기능

### Q: 복잡한 OR 조건을 어떻게 구성하나요?

**A:** SearchConditionBuilder를 사용하여 구성할 수 있습니다:

```java
// (title CONTAINS 'Spring' OR title CONTAINS 'Java') AND status = 'PUBLISHED'
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(group -> group.contains("title", "Spring"))
    .or(group -> group.contains("title", "Java"))
    .and(group -> group.equals("status", PostStatus.PUBLISHED))
    .build();
```

### Q: 동적으로 검색 조건을 추가하려면 어떻게 해야 하나요?

**A:** 조건부로 빌더를 사용하세요:

```java
public Page<Post> dynamicSearch(String title, PostStatus status, LocalDateTime fromDate) {
    ChainedSearchCondition<PostSearchDTO> builder = SearchConditionBuilder
        .create(PostSearchDTO.class);
        
    if (title != null && !title.isEmpty()) {
        builder = builder.where(group -> group.contains("title", title));
    }
    
    if (status != null) {
        builder = builder.and(group -> group.equals("status", status));
    }
    
    if (fromDate != null) {
        builder = builder.and(group -> group.greaterThan("createdAt", fromDate));
    }
    
    return findAllWithSearch(builder.build());
}
```

### Q: 중첩된 엔티티의 깊은 필드까지 검색할 수 있나요?

**A:** 네, 점 표기법을 사용하여 깊은 중첩까지 가능합니다:

```java
public class PostSearchDTO {
    // author.department.company.name까지 접근 가능
    @SearchableField(entityField = "author.department.company.name")
    private String companyName;
}
```

## 성능 및 최적화

### Q: 대용량 데이터에서 성능이 느린데 어떻게 개선할 수 있나요?

**A:** 다음 방법들을 시도해보세요:

1. **적절한 인덱스 생성**:
```sql
-- 검색 + 정렬 필드에 복합 인덱스
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);
```

2. **커서 페이징 활용**:
```java
// 내부적으로 커서 페이징이 자동 적용됨
public Page<Post> findPosts(SearchCondition<PostSearchDTO> condition) {
    return findAllWithSearch(condition);
}
```

3. **DTO 프로젝션 활용**:
```java
// 필요한 필드만 조회
public Page<PostSummaryDTO> findPostSummaries(SearchCondition<PostSearchDTO> condition) {
    return findAllWithSearch(condition, PostSummaryDTO.class);
}
```

### Q: N+1 문제가 발생할 수 있나요?

**A:** 연관 엔티티를 검색할 때 N+1 문제가 발생할 수 있습니다. 이를 해결하려면:

```java
@Entity
public class Post {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;
}

// Repository에서 fetch join 사용
@Query("SELECT p FROM Post p JOIN FETCH p.author WHERE ...")
Page<Post> findPostsWithAuthor(Specification<Post> spec, Pageable pageable);
```

### Q: 검색 조건이 복잡할 때 쿼리 성능을 어떻게 확인하나요?

**A:** 다음 설정으로 생성된 SQL을 확인할 수 있습니다:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    
spring:
  jpa:
    show-sql: true
    properties:
      hibernate:
        format_sql: true
```

## 에러 처리

### Q: "No qualifying bean of type 'SearchableService'" 에러가 발생합니다.

**A:** 다음을 확인하세요:

1. **Spring Boot Starter 의존성 확인**:
```gradle
implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:0.0.4-SNAPSHOT'
```

2. **서비스 클래스가 올바르게 정의되었는지 확인**:
```java
@Service
public class PostService extends DefaultSearchableService<Post> {
    public PostService(PostRepository repository) {
        super(repository, Post.class);
    }
}
```

### Q: "SearchableValidationException" 예외가 발생합니다.

**A:** 검색 조건 검증에 실패한 경우입니다. 다음을 확인하세요:

1. **허용되지 않은 연산자 사용**:
```java
// EQUALS만 허용했는데 CONTAINS 사용
@SearchableField(operators = {EQUALS})
private String title;

// 잘못된 요청: title.contains=Spring
// 올바른 요청: title.equals=Spring Boot
```

2. **존재하지 않는 필드 사용**:
```java
// DTO에 정의되지 않은 필드로 검색
GET /api/posts/search?nonExistentField.equals=value
```

### Q: JSON 파싱 에러가 발생합니다.

**A:** POST 방식 검색 시 JSON 형식을 확인하세요:

```json
// 올바른 형식
{
  "conditions": [
    {
      "operator": "and",
      "field": "title",
      "searchOperator": "contains", 
      "value": "Spring"
    }
  ]
}

// 잘못된 형식 - operator 오타
{
  "conditions": [
    {
      "operator": "AND", // 소문자 "and"여야 함
      "field": "title",
      "searchOperator": "contains",
      "value": "Spring"
    }
  ]
}
```

## 커서 페이징

### Q: 커서 페이징에서 특정 페이지로 바로 이동할 수 있나요?

**A:** 아니요. 커서 페이징의 특성상 순차적으로만 이동 가능합니다. 특정 페이지로의 직접 이동이 필요한 경우 일반 페이징을 사용하세요.

### Q: 커서가 만료되거나 무효해질 수 있나요?

**A:** 네, 다음 경우에 커서가 무효해질 수 있습니다:

- 정렬 기준이 된 데이터가 변경된 경우
- 커서가 가리키는 레코드가 삭제된 경우
- 너무 오래된 커서를 사용하는 경우

이런 경우 첫 페이지부터 다시 시작하세요.

### Q: 커서 페이징에서 전체 개수를 알 수 있나요?

**A:** 커서 페이징은 성능을 위해 전체 개수를 제공하지 않습니다. 전체 개수가 필요한 경우:

```java
// 별도로 카운트 조회
long totalCount = countWithSearch(condition);

// 또는 일반 페이징 사용
Page<Post> page = findAllWithSearch(condition);
long totalElements = page.getTotalElements();
```

## OpenAPI/Swagger

### Q: Swagger UI에서 검색 파라미터가 표시되지 않습니다.

**A:** 다음을 확인하세요:

1. **OpenAPI 모듈 의존성**:
```gradle
implementation 'dev.simplecore.searchable:searchable-jpa-openapi:0.0.4-SNAPSHOT'
```

2. **@SearchableParams 어노테이션 사용**:
```java
@GetMapping("/search")
public Page<Post> search(
    @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
) {
    // ...
}
```

### Q: 프로덕션 환경에서 Swagger를 비활성화하려면?

**A:** 프로파일별로 설정하세요:

```yaml
# application-prod.yml
springdoc:
  api-docs:
    enabled: false
  swagger-ui:
    enabled: false
```

## 기타

### Q: 트랜잭션 처리는 어떻게 되나요?

**A:** Searchable JPA는 기본적으로 읽기 전용이므로 `@Transactional(readOnly = true)`가 적용됩니다. 수정/삭제 작업 시에는 명시적으로 트랜잭션을 설정하세요:

```java
@Transactional
public long updatePosts(SearchCondition<PostSearchDTO> condition, PostUpdateDTO updateData) {
    return updateWithSearch(condition, updateData);
}
```

### Q: 다국어 지원은 되나요?

**A:** 현재 한국어와 영어 에러 메시지를 지원합니다. 추가 언어가 필요한 경우 메시지 파일을 확장할 수 있습니다:

```properties
# messages_ja.properties (일본어 추가)
validator.field.required=フィールドは必須です
```

### Q: 커스텀 검색 연산자를 추가할 수 있나요?

**A:** 현재 버전에서는 지원하지 않습니다. 향후 버전에서 확장 가능한 구조로 개선할 예정입니다. 현재는 기존 연산자를 조합하여 사용하세요.

### Q: 성능 모니터링은 어떻게 할 수 있나요?

**A:** Spring Boot Actuator와 함께 사용할 수 있습니다:

```gradle
implementation 'org.springframework.boot:spring-boot-starter-actuator'
```

```java
@Component
public class SearchMetrics {
    private final MeterRegistry meterRegistry;
    
    public void recordSearchTime(String searchType, long duration) {
        Timer.Sample.start(meterRegistry)
            .stop(Timer.builder("searchable.search.duration")
                .tag("type", searchType)
                .register(meterRegistry));
    }
}
```

---

더 많은 질문이나 문제가 있으시면 [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues)에 문의해 주세요.

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: API 레퍼런스](api-reference.md) 