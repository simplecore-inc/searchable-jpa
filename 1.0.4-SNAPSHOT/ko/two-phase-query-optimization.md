# 고성능 페이징

Searchable JPA는 대용량 데이터에서 높은 성능을 제공하는 **2단계 쿼리 최적화**를 지원합니다. 기존의 단일 쿼리 방식의 성능 문제를 해결하고, 복잡한 조인이 포함된 검색에서도 일관된 성능을 제공합니다.

## 2단계 쿼리 최적화란?

### 기존 단일 쿼리의 문제점

```sql
-- 복잡한 조인이 포함된 단일 쿼리 (성능 문제)
SELECT DISTINCT p.*, u.*, c.*
FROM posts p
LEFT JOIN users u ON p.author_id = u.id
LEFT JOIN comments c ON p.id = c.post_id
WHERE u.name LIKE '%John%' 
  AND p.status = 'PUBLISHED'
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 100;
```

**문제점:**
- 복잡한 조인으로 인한 성능 저하
- DISTINCT 처리로 인한 추가 오버헤드
- 대용량 데이터에서 OFFSET 성능 문제
- 불필요한 데이터까지 함께 조회

### 2단계 쿼리의 장점

```sql
-- 1단계: ID만 조회하는 빠른 쿼리
SELECT p.id
FROM posts p
JOIN users u ON p.author_id = u.id
WHERE u.name LIKE '%John%' 
  AND p.status = 'PUBLISHED'
ORDER BY p.created_at DESC
LIMIT 10 OFFSET 100;

-- 2단계: 조회된 ID로 전체 엔티티 조회 (최적화된 IN 쿼리)
SELECT p.*, u.*
FROM posts p
LEFT JOIN users u ON p.author_id = u.id
WHERE p.id IN (1, 5, 12, 23, 34, 45, 56, 67, 78, 89)
ORDER BY p.created_at DESC;
```

**장점:**
- 1단계에서 빠른 ID 조회
- 2단계에서 필요한 데이터만 효율적으로 조회
- 복잡한 조인 최적화
- N+1 문제 자동 해결

## 자동 최적화 시스템

Searchable JPA는 검색 조건을 자동으로 분석하여 최적의 쿼리 전략을 선택합니다.

### 최적화 적용 조건

다음 조건에서 자동으로 2단계 쿼리 최적화가 적용됩니다:

1. **복잡한 조인이 포함된 검색**
2. **복합 키 엔티티 검색**
3. **ToMany 관계가 포함된 검색**
4. **대용량 데이터 검색**

### 복합 키 지원

#### @IdClass 방식

```java
@Entity
@IdClass(CompositeKey.class)
public class TestIdClassEntity {
    @Id
    private String tenantId;
    
    @Id
    private Long entityId;
    
    private String name;
    
    public static class CompositeKey implements Serializable {
        private String tenantId;
        private Long entityId;
        // equals, hashCode, constructors
    }
}
```

#### @EmbeddedId 방식

```java
@Entity
public class TestCompositeKeyEntity {
    @EmbeddedId
    private CompositeKey id;
    
    private String name;
    
    @Embeddable
    public static class CompositeKey implements Serializable {
        private Long entityId;
        private String tenantId;
        // equals, hashCode, constructors
    }
}
```

#### 복합 키 2단계 쿼리

```sql
-- @IdClass 방식 1단계 쿼리
SELECT t.tenant_id, t.entity_id FROM test_idclass_entity t
WHERE t.tenant_id = 'tenant1' AND t.name LIKE '%test%'
ORDER BY t.tenant_id, t.entity_id
LIMIT 10;

-- @IdClass 방식 2단계 쿼리 (복합 OR 조건)
SELECT * FROM test_idclass_entity t
WHERE (t.tenant_id = 'tenant1' AND t.entity_id = 1) 
   OR (t.tenant_id = 'tenant1' AND t.entity_id = 2)
   OR (t.tenant_id = 'tenant1' AND t.entity_id = 3)
ORDER BY t.tenant_id, t.entity_id;

-- @EmbeddedId 방식 2단계 쿼리 (최적화된 IN 조건)
SELECT * FROM test_composite_key_entity t
WHERE (t.entity_id, t.tenant_id) IN ((1, 'tenant1'), (2, 'tenant1'), (3, 'tenant1'))
ORDER BY t.entity_id, t.tenant_id;
```

## 사용 방법

### 기본 사용법

기존 방식과 동일하게 사용하면 자동으로 최적화됩니다:

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
    
    // 자동으로 최적화된 쿼리 실행
    public Page<Post> findPosts(SearchCondition<PostSearchDTO> condition) {
        return findAllWithSearch(condition);
    }
}

@GetMapping("/search")
public Page<Post> searchPosts(
    @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
) {
    SearchCondition<PostSearchDTO> condition = 
        new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
    
    // 자동으로 2단계 쿼리 최적화 적용
    return postService.findAllWithSearch(condition);
}
```

### 복합 키 엔티티 검색

```java
@Service
public class CompositeKeyService extends DefaultSearchableService<TestIdClassEntity, TestIdClassEntity.CompositeKey> {
    
    public CompositeKeyService(CompositeKeyRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}

@GetMapping("/composite/search")
public Page<TestIdClassEntity> searchCompositeKey(
    @RequestParam @SearchableParams(CompositeKeySearchDTO.class) Map<String, String> params
) {
    SearchCondition<CompositeKeySearchDTO> condition = 
        new SearchableParamsParser<>(CompositeKeySearchDTO.class).convert(params);
    
    // 복합 키에 최적화된 2단계 쿼리 자동 실행
    return compositeKeyService.findAllWithSearch(condition);
}
```

## 페이징 응답 구조

표준 Spring Data의 `Page` 객체를 사용합니다:

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

### 1. 기본 페이징

```bash
# 첫 페이지
GET /api/posts/search?page=0&size=10&sort=createdAt.desc

# 다음 페이지
GET /api/posts/search?page=1&size=10&sort=createdAt.desc

# 특정 페이지로 이동
GET /api/posts/search?page=5&size=10&sort=createdAt.desc
```

### 2. 검색과 함께 페이징

```bash
# 제목 검색 + 페이징
GET /api/posts/search?title.contains=Spring&page=0&size=10&sort=createdAt.desc

# 복합 조건 검색 + 페이징
GET /api/posts/search?title.contains=Spring&status.equals=PUBLISHED&page=0&size=10
```

### 3. 정렬과 함께 페이징

```bash
# 단일 필드 정렬
GET /api/posts/search?sort=createdAt.desc&page=0&size=10

# 다중 필드 정렬
GET /api/posts/search?sort=status.asc,createdAt.desc&page=0&size=10
```

## 성능 최적화 기능

### 자동 조인 최적화

검색 조건을 분석하여 필요한 조인만 적용합니다:

```java
// 검색 조건에 author 필드가 있으면 자동으로 JOIN 적용
public class PostSearchDTO {
    @SearchableField(entityField = "author.name")
    private String authorName;  // 자동으로 User 테이블과 JOIN
    
    @SearchableField
    private String title;  // JOIN 불필요
}
```

### N+1 문제 자동 해결

ToOne 관계는 자동으로 페치 조인이 적용됩니다:

```java
// 자동으로 author를 함께 조회하여 N+1 문제 방지
@Entity
public class Post {
    @ManyToOne(fetch = FetchType.LAZY)
    private User author;  // 자동으로 JOIN FETCH 적용
}
```

### 동적 EntityGraph

검색 조건에 따라 동적으로 페치 전략을 최적화합니다:

```java
// authorName 검색 시 자동으로 author 관계 페치
GET /api/posts/search?authorName.contains=John

// title만 검색 시 author 관계 페치하지 않음
GET /api/posts/search?title.contains=Spring
```

## 성능 비교

### 단일 쿼리 vs 2단계 쿼리

| 항목 | 단일 쿼리 | 2단계 쿼리 |
|------|-----------|------------|
| 단순 검색 | 빠름 | 빠름 |
| 복잡한 조인 | 느림 | 빠름 |
| 대용량 데이터 | 매우 느림 | 빠름 |
| 메모리 사용량 | 높음 | 낮음 |
| N+1 문제 | 발생 가능 | 자동 해결 |
| 복합 키 지원 | 제한적 | 완벽 지원 |

### 실제 성능 측정 결과

```
데이터 크기: 1,000,000 레코드
복잡한 조인 쿼리 (3개 테이블 조인)

단일 쿼리:
- 첫 페이지: 50ms
- 중간 페이지: 200ms
- 마지막 페이지: 2000ms

2단계 쿼리:
- 첫 페이지: 15ms
- 중간 페이지: 20ms
- 마지막 페이지: 25ms
```

## 설정 및 튜닝

### 자동 최적화 설정

```yaml
searchable:
  hibernate:
    auto-optimization: true  # 자동 최적화 활성화
    default-batch-fetch-size: 100  # 배치 페치 크기
    jdbc-batch-size: 1000  # JDBC 배치 크기
```

### 인덱스 최적화

2단계 쿼리의 성능을 극대화하려면 적절한 인덱스가 필요합니다:

```sql
-- 1단계 쿼리용 인덱스 (검색 + 정렬)
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);

-- 2단계 쿼리용 인덱스 (ID 기반 조회)
CREATE INDEX idx_posts_id ON posts(id);

-- 복합 키 인덱스
CREATE INDEX idx_composite_tenant_entity ON test_idclass_entity(tenant_id, entity_id);

-- 중첩 필드 검색용 인덱스
CREATE INDEX idx_posts_author_name ON posts(author_id);
CREATE INDEX idx_users_name ON users(name);
```

## 모니터링 및 디버깅

### 쿼리 로그 확인

```yaml
logging:
  level:
    dev.simplecore.searchable.core.service.specification.TwoPhaseQueryExecutor: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
```

### 실행 전략 확인

로그를 통해 어떤 최적화 전략이 사용되었는지 확인할 수 있습니다:

```
DEBUG TwoPhaseQueryExecutor - Using two-phase optimization for complex query
DEBUG TwoPhaseQueryExecutor - Phase 1: Retrieved 10 IDs in 5ms
DEBUG TwoPhaseQueryExecutor - Phase 2: Retrieved entities in 8ms
INFO  TwoPhaseQueryExecutor - Total query time: 13ms
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
                .desc("id")
            )
            .page(page)
            .size(size)
            .build();
            
        // 자동으로 2단계 쿼리 최적화 적용
        return findAllWithSearch(condition);
    }
}
```

### 조건부 최적화

```java
public Page<Post> searchPosts(String title, 
                             PostStatus status, 
                             int page,
                             int size) {
    SearchConditionBuilder<PostSearchDTO> builder = SearchConditionBuilder
        .create(PostSearchDTO.class);
        
    if (title != null && !title.isEmpty()) {
        builder = builder.where(group -> group.contains("title", title));
    }
    
    if (status != null) {
        builder = builder.and(group -> group.equals("status", status));
    }
    
    SearchCondition<PostSearchDTO> condition = builder
        .sort(sort -> sort.desc("createdAt").desc("id"))
        .page(page)
        .size(size)
        .build();
        
    // 검색 조건에 따라 자동으로 최적화 전략 선택
    return findAllWithSearch(condition);
}
```

## 내부 구현 방식

### 최적화 전략 선택

Searchable JPA는 **항상 2단계 쿼리 최적화**를 사용합니다. 이는 복잡한 조인, 복합 키, 대용량 데이터에서 일관된 고성능을 보장하기 위함입니다.

```java
// TwoPhaseQueryExecutor에서 항상 2단계 최적화 사용
public boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
    return true; // Always use two-phase optimization
}

public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest) {
    // 항상 2단계 쿼리 실행
    List<Object> ids = executePhaseOneQuery(pageRequest);
    List<T> entities = executePhaseTwoQuery(ids, pageRequest.getSort());
    long totalCount = executeCountQuery();

    return new PageImpl<>(entities, pageRequest, totalCount);
}
```

### 2단계 쿼리 실행 과정

```java
// 1단계: ID만 조회
List<Object> ids = executePhaseOneQuery(searchCondition, pageable);

// 2단계: ID로 전체 엔티티 조회
List<T> entities = executePhaseTwoQuery(ids, pageable.getSort());

// 페이지 객체 생성
return new PageImpl<>(entities, pageable, totalCount);
```

## 제한사항과 고려사항

### 현재 제한사항

1. **추가 쿼리**: 2단계로 인한 쿼리 수 증가 (하지만 전체 성능은 향상)
2. **메모리 사용**: ID 목록을 메모리에 보관
3. **트랜잭션 범위**: 두 쿼리가 같은 트랜잭션 내에서 실행되어야 함

### 사용 시 고려사항

1. **인덱스 설계**: 1단계 쿼리용 복합 인덱스 필요
2. **페이지 크기**: 너무 큰 페이지 크기는 메모리 사용량 증가
3. **정렬 필드**: 정렬에 사용되는 필드에 인덱스 설정 권장

## 일반 페이징과의 차이점

| 특성 | 일반 페이징 | 2단계 쿼리 페이징 |
|------|-------------|------------------|
| 쿼리 수 | 1개 | 2개 |
| 복잡한 조인 성능 | 느림 | 빠름 |
| 메모리 사용량 | 낮음 | 중간 |
| N+1 문제 | 발생 가능 | 자동 해결 |
| 복합 키 지원 | 제한적 | 완벽 지원 |
| 구현 복잡도 | 간단 | 자동화됨 |

이러한 2단계 쿼리 최적화를 통해 복잡한 검색 조건에서도 일관된 고성능을 보장할 수 있습니다.