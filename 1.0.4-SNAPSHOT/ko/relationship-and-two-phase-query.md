# JPA 관계형 매핑과 2단계 쿼리 최적화

## 목차

1. [N+1 문제와 해결 방법](#n-1-문제와-해결책)
2. [JPA 관계형 매핑 개요](#jpa-관계형-매핑-개요)
3. [관계형 매핑별 특징](#관계형-매핑별-특징)
4. [2단계 쿼리 최적화 시스템](#2단계-쿼리-최적화-시스템)
5. [자동 Primary Key 정렬의 이유](#자동-primary-key-정렬의-이유)
6. [구현 상세](#구현-상세)

## 자동화된 최적화 전략

**searchable-jpa는 개발자가 성능 문제를 겪지 않도록 자동으로 최적화된 전략을 선택합니다.**

### 개발자 경험 우선

```java
@RestController
public class PostController {
    
    @Autowired
    private SearchableService<Post> postService;
    
    @GetMapping("/posts")
    public Page<Post> getPosts(
            @RequestParam(required = false) String title,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size) {

        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(w -> w.equals("title", title))
            .page(page)
            .size(size)
            .build();

        // 자동으로 2단계 쿼리 최적화 적용 - 복잡한 성능 최적화 고민 불필요
        return postService.findAllWithSearch(condition);
    }
}
```

### 자동화된 기능들

1. **자동 Primary Key 정렬**: 동일한 값으로 인한 레코드 누락 방지
2. **2단계 쿼리 최적화**: 모든 쿼리에 자동 적용으로 일관된 성능 보장
3. **JOIN 최적화**: ToOne은 Fetch Join, ToMany는 2단계 쿼리로 처리
4. **메모리 페이징 방지**: HHH000104 경고 자동 해결

### 내부 자동화 로직

```java
public Page<T> findAllWithSearch(SearchCondition<?> searchCondition) {
    SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
    return builder.buildAndExecuteWithTwoPhaseOptimization(); // 모든 쿼리에 2단계 최적화 적용
}
```

**2단계 쿼리 최적화 적용:**

```
모든 검색 쿼리
    ↓
2단계 쿼리 최적화 적용
    ↓
┌─────────────────────────────────────┐
│ 1단계: ID만 조회                    │ → 조건 + 정렬 + 페이징으로 ID 목록 조회
├─────────────────────────────────────┤
│ 2단계: 전체 엔티티 조회             │ → 조회된 ID로 IN 쿼리 실행
├─────────────────────────────────────┤
│ 3단계: 카운트 쿼리                  │ → 정확한 총 개수 조회
└─────────────────────────────────────┘
```

---

## JPA 관계형 매핑 개요

JPA에서 엔티티 간의 관계는 네 가지 유형으로 분류됩니다:

### OneToOne (일대일)
```java
@Entity
public class User {
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;
}

@Entity
public class UserProfile {
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;
}
```

### OneToMany (일대다)
```java
@Entity
public class Post {
    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    private List<Comment> comments = new ArrayList<>();
}

@Entity
public class Comment {
    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;
}
```

### ManyToOne (다대일)
```java
@Entity
public class Comment {
    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;
    
    @ManyToOne
    @JoinColumn(name = "author_id")
    private Author author;
}
```

### ManyToMany (다대다)
```java
@Entity
public class Post {
    @ManyToMany
    @JoinTable(
        name = "post_tag",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();
}

@Entity
public class Tag {
    @ManyToMany(mappedBy = "tags")
    private Set<Post> posts = new HashSet<>();
}
```

---

## N+1 문제와 해결책

### N+1 문제란?
N+1 문제는 연관된 엔티티를 조회할 때 발생하는 성능 문제입니다:

```java
// 1번의 쿼리로 Post 목록 조회
List<Post> posts = postRepository.findAll();

// 각 Post마다 Author를 조회하는 N번의 추가 쿼리 발생
for (Post post : posts) {
    String authorName = post.getAuthor().getName(); // N번의 쿼리!
}
```

### searchable-jpa의 자동 N+1 방지

searchable-jpa는 관계형 필드가 검색 조건이나 정렬에 사용될 때 **자동으로 JOIN을 처리**합니다:

```java
// 이 검색 조건은 자동으로 JOIN을 생성합니다
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(w -> w.contains("authorName", "John"))
    .sort(s -> s.asc("authorName"))
    .build();
```

**생성되는 SQL:**
```sql
-- 1단계: ID만 조회 (JOIN 포함)
SELECT DISTINCT p.id 
FROM post p 
LEFT JOIN author a ON p.author_id = a.id 
WHERE LOWER(a.name) LIKE '%john%' 
ORDER BY a.name ASC, p.id ASC
LIMIT 10 OFFSET 0;

-- 2단계: 전체 엔티티 조회
SELECT p.*, a.*
FROM post p
LEFT JOIN author a ON p.author_id = a.id
WHERE p.id IN (1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
ORDER BY a.name ASC, p.id ASC;
```

#### 자동 JOIN 처리 전략

searchable-jpa는 **자동으로 최적화된 JOIN 전략**을 사용합니다:

**핵심 원리:**
```java
public Page<T> findAllWithSearch(SearchCondition<?> searchCondition) {
    // 모든 쿼리에 2단계 최적화 자동 적용
    SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
    return builder.buildAndExecuteWithTwoPhaseOptimization();
}
```

**자동 최적화 로직:**
```java
public Page<T> buildAndExecuteWithTwoPhaseOptimization() {
    PageRequest pageRequest = buildPageRequest();
    
    // 모든 쿼리에 2단계 최적화 적용
    return twoPhaseQueryExecutor.executeWithTwoPhaseOptimization(pageRequest);
}

public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest) {
    // Phase 1: Get IDs only
    List<Object> ids = executePhaseOneQuery(pageRequest);
    
    if (ids.isEmpty()) {
        return new PageImpl<>(Collections.emptyList(), pageRequest, 0);
    }

    // Phase 2: Get full entities using batched IN clauses
    List<T> entities = executePhaseTwoQuery(ids, pageRequest.getSort());
    
    // Phase 3: Get total count for accurate pagination
    long totalCount = executeCountQuery();
    
    return new PageImpl<>(entities, pageRequest, totalCount);
}
```

---

## 관계형 매핑별 특징

### OneToOne 관계
**자동 최적화:**
- N+1 문제 자동 방지 (Fetch Join)
- 성능 최적화 우수

**주의사항:**
- 양방향 관계 시 무한 루프 주의

### OneToMany 관계
**자동 최적화:**
- 자동 2단계 쿼리로 성능 문제 해결
- 메모리 페이징 문제 자동 방지

**특징:**
- 복수 OneToMany 관계 시에도 2단계 쿼리로 안전하게 처리

### ManyToOne 관계
**자동 최적화:**
- 가장 안전하고 성능이 좋음
- 자동 Fetch Join으로 N+1 방지

**특징:**
- 특별한 주의사항 없음 (권장)

### ManyToMany 관계
**자동 최적화:**
- HHH000104 경고 자동 해결
- 2단계 쿼리로 메모리 페이징 방지
- 카티시안 곱 문제 자동 해결

**추가 최적화 옵션:**
1. **DTO 프로젝션 사용** (더 나은 성능):
```java
@SearchableField(entityField = "tags.name")
private String tagNames; // 태그명들을 문자열로 조회
```

2. **배치 크기 설정** (2단계 쿼리와 함께):
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100
```

---

## 동적 EntityGraph

### EntityGraph 자동 생성

searchable-jpa는 검색 조건에 사용된 관계형 필드들을 기반으로 **동적 EntityGraph**를 자동 생성합니다:

```java
// 검색 조건에 관계형 필드가 포함된 경우
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(w -> w
        .contains("authorName", "John")  // author 관계 사용
        .equals("categoryName", "Tech")) // category 관계 사용
    .build();

// 자동으로 EntityGraph 생성 및 적용
// ToOne 관계만 포함 (author, category)
// ToMany 관계 제외 (메모리 페이징 방지)
```

### EntityGraph 최적화 전략

**포함되는 관계:**
- `@OneToOne`, `@ManyToOne` 관계
- 중첩된 ToOne 관계 (`author.department.name`)

**제외되는 관계:**
- `@OneToMany`, `@ManyToMany` 관계
- ToMany 관계는 2단계 쿼리로 처리

```java
/**
 * 동적 EntityGraph 생성 로직
 */
public jakarta.persistence.EntityGraph<T> createDynamicEntityGraph(Set<String> relationshipPaths) {
    jakarta.persistence.EntityGraph<T> entityGraph = entityManager.createEntityGraph(entityClass);

    for (String path : relationshipPaths) {
        // ToOne 관계만 EntityGraph에 추가
        if (!isToManyPath(path)) {
            entityGraph.addAttributeNodes(path);
        }
    }

    return entityGraph;
}
```

### EntityGraph 적용 시점

EntityGraph는 다음 경우에 자동으로 적용됩니다:

1. **관계형 필드 검색**: `author.name`, `category.title` 등
2. **중첩 관계 검색**: `author.department.name`
3. **다중 관계 검색**: 여러 ToOne 관계 동시 검색

### 메모리 효율성

```sql
-- EntityGraph 적용 전 (N+1 문제 가능)
SELECT p.* FROM posts p WHERE p.title LIKE '%Spring%';
-- 각 Post마다 Author 조회 (N+1 문제!)

-- EntityGraph 적용 후 (최적화)
SELECT p.*, a.* FROM posts p
LEFT JOIN author a ON p.author_id = a.id
WHERE p.title LIKE '%Spring%';
-- 한 번의 쿼리로 모든 데이터 조회
```

**EntityGraph의 장점:**
- ✔ N+1 문제 자동 방지
- ✔ 메모리 효율성 향상
- ✔ 쿼리 수 최소화
- ✔ ToMany 관계에 의한 카티시안 곱 방지

---

## 2단계 쿼리 최적화 시스템

### 2단계 쿼리의 장점

**1. 일정한 성능**
```sql
-- 1단계: 항상 빠른 ID 조회
SELECT p.id FROM posts p WHERE p.status = 'PUBLISHED' 
ORDER BY p.created_at DESC LIMIT 10 OFFSET 100;

-- 2단계: 효율적인 IN 쿼리
SELECT p.*, a.* FROM posts p 
LEFT JOIN author a ON p.author_id = a.id
WHERE p.id IN (101, 102, 103, 104, 105, 106, 107, 108, 109, 110);
```

**2. 메모리 효율성**
- 1단계에서 필요한 ID만 조회
- 2단계에서 실제 필요한 데이터만 로드

**3. 복합 키 지원**
```sql
-- @IdClass 방식
SELECT * FROM test_idclass_entity t
WHERE (t.tenant_id = 'tenant1' AND t.entity_id = 1) 
   OR (t.tenant_id = 'tenant1' AND t.entity_id = 2);

-- @EmbeddedId 방식 (최적화된 IN 조건)
SELECT * FROM test_composite_key_entity t
WHERE (t.entity_id, t.tenant_id) IN ((1, 'tenant1'), (2, 'tenant1'));
```

---

## 자동 Primary Key 정렬의 이유

### 문제 상황: 동일한 정렬 값

```java
// 생성일시로만 정렬할 경우
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .sort(s -> s.desc("createdAt"))
    .build();
```

**문제가 되는 데이터:**
```
ID | CREATED_AT          | TITLE
1  | 2023-01-01 10:00:00 | Post A
2  | 2023-01-01 10:00:00 | Post B  // 동일한 시간!
3  | 2023-01-01 10:00:00 | Post C  // 동일한 시간!
4  | 2023-01-01 09:00:00 | Post D
```

**1페이지 결과 (LIMIT 2):**
```
[Post A, Post B] // cursor = '2023-01-01 10:00:00'
```

**2페이지 쿼리:**
```sql
SELECT * FROM posts 
WHERE created_at < '2023-01-01 10:00:00'  -- Post C가 제외됨!
ORDER BY created_at DESC LIMIT 2;
```

**2페이지 결과:**
```
[Post D, ...] // Post C가 누락됨!
```

### 해결책: 자동 Primary Key 정렬

searchable-jpa는 **자동으로 Primary Key를 보조 정렬 기준으로 추가**합니다:

```java
// 사용자 입력
.sort(s -> s.desc("createdAt"))

// 자동 변환 (내부적으로 처리됨)
.sort(s -> s.desc("createdAt"))
.sort(s -> s.asc("id"))  // 자동 추가!
```

**생성되는 SQL:**
```sql
-- 1단계: ID 조회
SELECT p.id FROM posts p
ORDER BY p.created_at DESC, p.id ASC LIMIT 2 OFFSET 0;
-- 결과: [1, 2]

-- 2단계: 전체 엔티티 조회
SELECT * FROM posts p WHERE p.id IN (1, 2)
ORDER BY p.created_at DESC, p.id ASC;
-- 결과: [Post A(id=1), Post B(id=2)]
```

이렇게 하면 **모든 레코드가 누락 없이 일관된 순서로 조회**됩니다.

---

## 구현 상세

### Primary Key 자동 감지

```java
/**
 * Ensures unique sorting by adding primary key field if not already present.
 * This is crucial for consistent pagination to work correctly.
 */
private List<Sort.Order> ensureUniqueSorting(List<Sort.Order> sortOrders) {
    try {
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);

        if (primaryKeyField != null) {
            // Check if primary key field is already in sort orders
            boolean hasPrimaryKey = sortOrders.stream()
                    .anyMatch(order -> primaryKeyField.equals(order.getProperty()));

            if (!hasPrimaryKey) {
                // Add primary key field as the last sort criterion in ascending order
                sortOrders = new ArrayList<>(sortOrders);
                sortOrders.add(Sort.Order.by(primaryKeyField));

                log.debug("Automatically added primary key field '{}' to sort criteria for cursor-based pagination uniqueness",
                        primaryKeyField);
            }
        } else {
            log.warn("Could not determine primary key field for entity {}. Cursor-based pagination may not work correctly with duplicate sort values.",
                    entityClass.getSimpleName());
        }

        return sortOrders;

    } catch (Exception e) {
        log.warn("Failed to ensure unique sorting for entity {}: {}. Using original sort orders.",
                entityClass.getSimpleName(), e.getMessage());
        return sortOrders;
    }
}
```

### 2단계 쿼리 실행 과정

```java
public Page<T> executeWithTwoPhaseOptimization(PageRequest pageRequest) {
    // Phase 1: Get IDs only with conditions and pagination
    List<Object> ids = executePhaseOneQuery(pageRequest);
    
    if (ids.isEmpty()) {
        return new PageImpl<>(Collections.emptyList(), pageRequest, 0);
    }

    // Phase 2: Get full entities using batched IN clauses
    List<T> entities = executePhaseTwoQuery(ids, pageRequest.getSort());
    
    // Phase 3: Get total count for accurate pagination
    long totalCount = executeCountQuery();
    
    return new PageImpl<>(entities, pageRequest, totalCount);
}
```

### 배치 처리 최적화

```java
// 대용량 ID 목록을 배치로 분할하여 처리
private static final int MAX_IN_CLAUSE_SIZE = 500;

private List<T> executeBatchedInQueries(List<Object> ids, Sort sort) {
    List<T> allResults = new ArrayList<>();

    // Split IDs into batches
    for (int i = 0; i < ids.size(); i += MAX_IN_CLAUSE_SIZE) {
        int endIndex = Math.min(i + MAX_IN_CLAUSE_SIZE, ids.size());
        List<Object> batchIds = ids.subList(i, endIndex);

        log.debug("Executing batch {}/{} with {} IDs",
            (i / MAX_IN_CLAUSE_SIZE) + 1,
            (ids.size() + MAX_IN_CLAUSE_SIZE - 1) / MAX_IN_CLAUSE_SIZE,
            batchIds.size());

        // Execute query for this batch
        List<T> batchResults = executeSingleInQuery(batchIds, sort);
        allResults.addAll(batchResults);
    }

    log.debug("Executed {} batches, total results: {}",
        (ids.size() + MAX_IN_CLAUSE_SIZE - 1) / MAX_IN_CLAUSE_SIZE,
        allResults.size());

    // Final ordering is maintained by reorderEntitiesByIds in executeSingleInQuery
    return allResults;
}
```