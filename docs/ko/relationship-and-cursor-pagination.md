# JPA 관계형 매핑과 커서 기반 페이징

## 목차
1. [자동화된 최적화 전략](#자동화된-최적화-전략)
2. [JPA 관계형 매핑 개요](#jpa-관계형-매핑-개요)
3. [N+1 문제와 해결책](#n1-문제와-해결책)
4. [관계형 매핑별 특징](#관계형-매핑별-특징)
5. [커서 기반 페이징의 필요성](#커서-기반-페이징의-필요성)
6. [자동 Primary Key 정렬의 이유](#자동-primary-key-정렬의-이유)
7. [구현 상세](#구현-상세)
8. [2단계 쿼리 최적화 전략](#2단계-쿼리-최적화-전략)
9. [성능 최적화 가이드](#성능-최적화-가이드)
10. [실제 사용 예시](#실제-사용-예시)
11. [ManyToMany N+1 문제 완전 해결](#manytomany-n1-문제-완전-해결)

---

## 자동화된 최적화 전략

**searchable-jpa는 개발자가 성능 문제를 겪지 않도록 자동으로 최적화된 전략을 선택합니다.**

### 개발자 경험 우선

```java
@RestController
public class PostController {
    
    @Autowired
    private SearchableService<Post> postService;
    
    @GetMapping("/posts")
    public Page<Post> getPosts(@RequestParam String search) {
        SearchCondition condition = SearchCondition.of(search);
        
        // 자동으로 최적화된 전략 사용 - 복잡한 성능 최적화 고민 불필요
        return postService.findAllWithSearch(condition);
    }
}
```

### 자동화된 기능들

1. **자동 Primary Key 정렬**: 동일한 값으로 인한 레코드 누락 방지
2. **스마트 쿼리 전략**: ToMany 관계 감지하여 2단계 쿼리 자동 적용
3. **JOIN 최적화**: ToOne은 Fetch Join, ToMany는 스마트 전략 적용
4. **메모리 페이징 방지**: HHH000104 경고 자동 해결

### 내부 자동화 로직

```java
public Page<T> findAllWithSearch(SearchCondition<?> searchCondition) {
    SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
    return builder.buildAndExecuteWithTwoPhaseOptimization(); // 자동 최적화
}
```

**자동 전략 선택 흐름:**

```
검색 조건 분석
    ↓
ToMany 관계 감지
    ↓
최적 전략 자동 선택
    ↓
┌─────────────────────────────────────┐
│ ToMany 관계 없음                    │ → 단일 쿼리 (최고 성능)
├─────────────────────────────────────┤
│ 단일 ToMany + 단순 조건             │ → 단일 쿼리 (안전)
├─────────────────────────────────────┤
│ 단일 ToMany + 복잡 조건             │ → 2단계 쿼리 (안전)
├─────────────────────────────────────┤
│ 복수 ToMany 관계                    │ → 2단계 쿼리 (필수)
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
SearchCondition condition = SearchCondition.builder()
    .filter("author.name", SearchOperator.CONTAINS, "John")
    .sort("author.name", SortDirection.ASC)
    .build();
```

**생성되는 SQL:**
```sql
SELECT DISTINCT p.* 
FROM post p 
LEFT JOIN author a ON p.author_id = a.id 
WHERE LOWER(a.name) LIKE '%john%' 
ORDER BY a.name ASC
```

#### 자동 JOIN 처리 전략

searchable-jpa는 **자동으로 최적화된 JOIN 전략**을 사용합니다:

**핵심 원리:**
```java
public Page<T> findAllWithSearch(SearchCondition<?> searchCondition) {
    // 자동으로 최적화된 전략 사용
    SearchableSpecificationBuilder<T> builder = createSpecificationBuilder(searchCondition);
    return builder.buildAndExecuteWithTwoPhaseOptimization(); // 자동 최적화
}
```

**자동 최적화 로직:**
```java
public Page<T> buildAndExecuteWithTwoPhaseOptimization() {
    Set<String> joinPaths = extractJoinPaths(condition.getNodes());
    Set<String> toManyPaths = joinPaths.stream()
            .filter(path -> isToManyPath(createDummyRoot(), path))
            .collect(Collectors.toSet());
    
    // 자동 전략 선택
    if (shouldUseTwoPhaseQuery(toManyPaths)) {
        return executeTwoPhaseQuery(pageRequest, joinPaths); // 2단계 쿼리
    } else {
        return buildAndExecuteWithCursor(); // 단일 쿼리
    }
}

private boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
    // 복수 ToMany 관계 → 2단계 쿼리 (필수)
    if (toManyPaths.size() >= 2) {
        return true;
    }
    
    // 단일 ToMany + 복잡한 조건 → 2단계 쿼리 (안전)
    if (toManyPaths.size() == 1) {
        return hasComplexConditions();
    }
    
    return false; // ToMany 없음 → 단일 쿼리 (최고 성능)
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
- 복수 OneToMany 관계 시 자동으로 2단계 쿼리 적용

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

## 커서 기반 페이징의 필요성

### OFFSET 기반 페이징의 문제점

**1. 성능 저하 (Deep Pagination)**
```sql
-- 100만 번째 페이지 조회 시
SELECT * FROM posts ORDER BY created_at DESC LIMIT 20 OFFSET 20000000;
-- 데이터베이스가 2천만 개 레코드를 스캔해야 함!
```

**2. 데이터 일관성 문제**
```
페이지 1 조회: [A, B, C, D, E]
새 데이터 X 삽입
페이지 2 조회: [C, D, E, F, G] // C, D, E가 중복 조회됨!
```

**3. 실시간 데이터 변경 시 누락**
```
페이지 1 조회 후 데이터 삭제 발생
페이지 2 조회 시 일부 데이터가 누락됨
```

### 커서 기반 페이징의 장점

**1. 일정한 성능**
```sql
-- 항상 인덱스를 효율적으로 사용
SELECT * FROM posts WHERE created_at < '2023-01-01 12:00:00' 
ORDER BY created_at DESC LIMIT 20;
```

**2. 데이터 일관성 보장**
```
커서 기반: WHERE created_at < 'cursor_value'
새 데이터가 삽입되어도 이전 페이지 결과에 영향 없음
```

**3. 실시간 스트리밍에 적합**
```
무한 스크롤, 실시간 피드 등에 최적화
```

---

## 자동 Primary Key 정렬의 이유

### 문제 상황: 동일한 정렬 값

```java
// 생성일시로만 정렬할 경우
SearchCondition condition = SearchCondition.builder()
    .sort("createdAt", SortDirection.DESC)
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
.sort("createdAt", SortDirection.DESC)

// 자동 변환
.sort("createdAt", SortDirection.DESC)
.sort("id", SortDirection.ASC)  // 자동 추가!
```

**생성되는 SQL:**
```sql
-- 1페이지
SELECT * FROM posts 
ORDER BY created_at DESC, id ASC LIMIT 2;
-- 결과: [Post A(id=1), Post B(id=2)]

-- 2페이지  
SELECT * FROM posts 
WHERE (created_at < '2023-01-01 10:00:00') 
   OR (created_at = '2023-01-01 10:00:00' AND id > 2)
ORDER BY created_at DESC, id ASC LIMIT 2;
-- 결과: [Post C(id=3), Post D(id=4)]
```

이렇게 하면 **모든 레코드가 누락 없이 조회**됩니다.

---

## 구현 상세

### Primary Key 자동 감지

```java
private String getPrimaryKeyFieldName() {
    try {
        // 1. JPA 메타모델에서 ID 속성 찾기
        EntityType<T> entityType = entityManager.getMetamodel().entity(entityClass);
        SingularAttribute<? super T, ?> idAttribute = entityType.getId(entityType.getIdType().getJavaType());
        return idAttribute.getName();
    } catch (Exception e) {
        // 2. 리플렉션으로 @Id 어노테이션 찾기
        for (Field field : entityClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Id.class)) {
                return field.getName();
            }
        }
        
        // 3. 일반적인 ID 필드명으로 fallback
        String[] commonIdFields = {"id", "pk", entityClass.getSimpleName().toLowerCase() + "Id"};
        for (String fieldName : commonIdFields) {
            try {
                entityClass.getDeclaredField(fieldName);
                return fieldName;
            } catch (NoSuchFieldException ignored) {}
        }
        
        return "id"; // 기본값
    }
}
```

### 자동 정렬 추가 로직

```java
private List<Sort.Order> ensureUniqueSorting(List<Sort.Order> sortOrders) {
    String primaryKeyField = getPrimaryKeyFieldName();
    
    // Primary Key가 이미 포함되어 있는지 확인
    boolean hasPrimaryKey = sortOrders.stream()
        .anyMatch(order -> order.getProperty().equals(primaryKeyField));
    
    if (!hasPrimaryKey) {
        // Primary Key를 ASC 순서로 추가
        List<Sort.Order> result = new ArrayList<>(sortOrders);
        result.add(Sort.Order.asc(primaryKeyField));
        return result;
    }
    
    return sortOrders;
}
```

### 커서 조건 생성

```java
private Predicate createCursorCondition(Root<T> root, CriteriaBuilder cb, 
                                       List<Sort.Order> sortOrders, Map<String, Object> cursorValues) {
    List<Predicate> orConditions = new ArrayList<>();
    
    for (int i = 0; i < sortOrders.size(); i++) {
        List<Predicate> andConditions = new ArrayList<>();
        
        // 이전 필드들은 동등 조건
        for (int j = 0; j < i; j++) {
            Sort.Order order = sortOrders.get(j);
            Object value = cursorValues.get(order.getProperty());
            andConditions.add(cb.equal(root.get(order.getProperty()), value));
        }
        
        // 현재 필드는 부등호 조건
        Sort.Order currentOrder = sortOrders.get(i);
        Object currentValue = cursorValues.get(currentOrder.getProperty());
        
        if (currentOrder.isAscending()) {
            andConditions.add(cb.greaterThan(root.get(currentOrder.getProperty()), 
                                           (Comparable) currentValue));
        } else {
            andConditions.add(cb.lessThan(root.get(currentOrder.getProperty()), 
                                        (Comparable) currentValue));
        }
        
        orConditions.add(cb.and(andConditions.toArray(new Predicate[0])));
    }
    
    return cb.or(orConditions.toArray(new Predicate[0]));
}
```

---

## 2단계 쿼리 최적화 전략

### 문제점 분석

#### 1. MultipleBagFetchException과 HHH000104 경고
```
MultipleBagFetchException: cannot simultaneously fetch multiple bags
HHH000104: firstResult/maxResults specified with collection fetch; applying in memory!
```

**발생 원인**:
- 복수의 ToMany 관계에서 동시 Fetch Join 시도
- 카티시안 곱으로 인한 결과 집합 폭증 (Post 1개 × Tag 5개 × Comment 3개 = 15개 행)
- Hibernate가 메모리에서 페이징 처리

**문제점**:
- 데이터베이스 레벨 LIMIT 무효화
- 메모리 사용량 급증 (수만 건 데이터에서 수십만~수백만 행 생성)
- 성능 저하 및 정확성 문제

#### 2. 실제 성능 문제 시나리오
```
데이터 규모:
- Posts: 100,000건
- 각 Post당 평균 Tag: 5개
- 각 Post당 평균 Comment: 10개

Regular Join 시 생성되는 행:
100,000 × 5 × 10 = 5,000,000 행 (500만 행!)
```

### 해결책: 2단계 쿼리 전략

#### Phase 1: ID 수집 (Regular Join)
```sql
-- 효율적인 ID 수집 (카티시안 곱 발생하지만 ID만 조회)
SELECT DISTINCT p.post_id, p.created_at
FROM posts p 
LEFT JOIN post_tags pt ON p.id = pt.post_id
LEFT JOIN tags t ON pt.tag_id = t.id
LEFT JOIN comments c ON p.id = c.post_id
WHERE t.name LIKE '%Java%'
  AND c.content LIKE '%Spring%'
ORDER BY p.created_at DESC, p.post_id ASC
LIMIT 20; -- 데이터베이스에서 정상 적용
```

#### Phase 2: 완전한 데이터 로딩 (Smart Fetch Join)
```sql
-- 수집된 ID로 완전한 엔티티 조회
SELECT DISTINCT p.*, c.*
FROM posts p 
LEFT JOIN FETCH comments c ON p.id = c.post_id  -- 첫 번째 ToMany만 Fetch
WHERE p.id IN (1, 5, 12, 18, ...) -- Phase 1에서 수집된 ID들
ORDER BY p.created_at DESC, p.post_id ASC;

-- 나머지 ToMany는 배치로 별도 조회
SELECT pt.post_id, t.*
FROM post_tags pt
LEFT JOIN tags t ON pt.tag_id = t.id  
WHERE pt.post_id IN (1, 5, 12, 18, ...);
```

### 실제 구현 코드

#### Phase 1: ID 수집 쿼리
```java
private List<Object> executePhaseOneQuery(PageRequest pageRequest) {
    log.debug("Phase 1: Starting ID-only query");
    
    // Only extract join paths from conditions (no additional ToOne fields)
    Set<String> conditionJoinPaths = extractJoinPaths(condition.getNodes());
    log.debug("Phase 1: Extracted join paths from conditions: {}", conditionJoinPaths);
    
    // Get primary key field and sorting fields
    String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
    List<Sort.Order> sortOrders = createSort(pageRequest.getSort()).toList();
    
    // Create criteria query that selects IDs and sorting fields
    CriteriaBuilder cb = entityManager.getCriteriaBuilder();
    
    // Determine query type based on additional sorting fields
    Set<String> sortFields = sortOrders.stream()
            .map(Sort.Order::getProperty)
            .filter(field -> !field.equals(primaryKeyField))
            .collect(Collectors.toSet());
    
    if (sortFields.isEmpty()) {
        // Only primary key sorting - use Object query
        CriteriaQuery<Object> query = cb.createQuery(Object.class);
        Root<T> root = query.from(entityClass);
        
        // Apply regular joins for conditions
        applyRegularJoinsOnly(root, conditionJoinPaths);
        
        // Select only primary key
        query.select(root.get(primaryKeyField));
        query.distinct(true);
        
        // Apply all search conditions
        Predicate predicate = createPredicates(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        
        // Apply sorting
        List<Order> orders = createCriteriaOrders(cb, root, sortOrders);
        query.orderBy(orders);
        
        // Execute query
        TypedQuery<Object> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());
        
        List<Object> results = typedQuery.getResultList();
        log.debug("Phase 1: Retrieved {} IDs", results.size());
        
        return results;
    } else {
        // Additional sorting fields exist - use Object[] query
        CriteriaQuery<Object[]> query = cb.createQuery(Object[].class);
        Root<T> root = query.from(entityClass);
        
        // Apply regular joins for conditions
        applyRegularJoinsOnly(root, conditionJoinPaths);
        
        // Select primary key and sorting fields
        List<Selection<?>> selections = new ArrayList<>();
        selections.add(root.get(primaryKeyField));
        for (String sortField : sortFields) {
            selections.add(root.get(sortField));
        }
        
        query.multiselect(selections);
        query.distinct(true);
        
        // Apply all search conditions
        Predicate predicate = createPredicates(root, query, cb);
        if (predicate != null) {
            query.where(predicate);
        }
        
        // Apply sorting
        List<Order> orders = createCriteriaOrders(cb, root, sortOrders);
        query.orderBy(orders);
        
        // Execute query
        TypedQuery<Object[]> typedQuery = entityManager.createQuery(query);
        typedQuery.setFirstResult((int) pageRequest.getOffset());
        typedQuery.setMaxResults(pageRequest.getPageSize());
        
        List<Object[]> results = typedQuery.getResultList();
        log.debug("Phase 1: Retrieved {} ID arrays", results.size());
        
        // Extract IDs from Object[] results
        return results.stream()
                .map(row -> row[0]) // First element is always the ID
                .collect(Collectors.toList());
    }
}
```

#### Phase 2: 완전한 엔티티 로딩
```java
private List<T> executePhaseTwoQuery(List<Object> entityIds, Set<String> allJoinPaths, Sort sort) {
    log.debug("Phase 2: Starting full entity query with {} IDs", entityIds.size());
    
    Specification<T> fullDataSpec = (root, query, cb) -> {
        // Apply SMART fetch joins to avoid MultipleBagFetchException
        applySmartFetchJoins(root, allJoinPaths);
        query.distinct(true);
        
        // Filter by collected IDs
        String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
        return root.get(primaryKeyField).in(entityIds);
    };
    
    // Execute with sorting
    List<T> entities = specificationExecutor.findAll(fullDataSpec, sort);
    log.debug("Phase 2: Retrieved {} entities", entities.size());
    
    // Reorder entities to match original ID order
    return reorderEntitiesByIds(entities, entityIds);
}
```

#### ID 순서대로 엔티티 재정렬
```java
private List<T> reorderEntitiesByIds(List<T> entities, List<Object> orderedIds) {
    log.debug("Reordering entities: {} entities, {} ordered IDs", entities.size(), orderedIds.size());
    
    if (entities.size() != orderedIds.size()) {
        log.warn("Entity count ({}) does not match ordered ID count ({}). This may be due to DISTINCT removing duplicates.", entities.size(), orderedIds.size());
        // Don't return early - proceed with reordering using available entities
    }
    
    // Create a map for O(1) lookup
    Map<Object, T> entityMap = new HashMap<>();
    String primaryKeyField = SearchableFieldUtils.getPrimaryKeyFieldName(entityManager, entityClass);
    log.debug("Detected primary key field: {}", primaryKeyField);
    
    for (T entity : entities) {
        try {
            Object id = null;
            
            // Try using getId() method first (handles Hibernate proxies better)
            try {
                Method getIdMethod = entity.getClass().getMethod("getId");
                id = getIdMethod.invoke(entity);
                log.debug("Extracted ID via getId() method: {}", id);
            } catch (Exception getIdException) {
                log.debug("getId() method not available, falling back to reflection: {}", getIdException.getMessage());
                // Fallback to reflection
                Field idField = entityClass.getDeclaredField(primaryKeyField);
                idField.setAccessible(true);
                id = idField.get(entity);
                log.debug("Extracted ID via reflection: {}", id);
            }
            
            if (id != null) {
                entityMap.put(id, entity);
                log.debug("Added entity to map: ID={}", id);
            } else {
                log.warn("Failed to extract ID from entity: {}", entity);
            }
        } catch (Exception e) {
            log.error("Failed to extract ID from entity: {}", entity, e);
            return entities; // Return original order on error
        }
    }
    
    // Reorder according to original ID sequence
    List<T> reorderedEntities = new ArrayList<>();
    for (Object id : orderedIds) {
        T entity = entityMap.get(id);
        if (entity != null) {
            reorderedEntities.add(entity);
            log.debug("Added entity for ID: {}", id);
        } else {
            log.warn("No entity found for ID: {}", id);
        }
    }
    
    log.debug("Final reordered entity count: {}", reorderedEntities.size());
    return reorderedEntities;
}
```

#### 자동 전략 선택 로직
```java
private boolean shouldUseTwoPhaseQuery(Set<String> toManyPaths) {
    // 복수 ToMany 관계 → 무조건 2단계 쿼리
    if (toManyPaths.size() >= 2) {
        return true; // MultipleBagFetchException 방지
    }
    
    // 단일 ToMany + 복잡한 조건 → 2단계 쿼리
    if (toManyPaths.size() == 1) {
        return hasComplexConditions();
    }
    
    return false; // ToMany 없음 → 단일 쿼리
}

private boolean hasComplexConditions() {
    if (condition.getNodes() == null) return false;
    
    // ToMany 관계를 사용하는 조건 개수 계산
    long toManyConditionCount = condition.getNodes().stream()
            .filter(node -> node instanceof SearchCondition.Condition)
            .map(node -> (SearchCondition.Condition) node)
            .filter(cond -> {
                String entityField = cond.getEntityField();
                return entityField != null && entityField.contains(".") && 
                       isToManyPath(createDummyRoot(), getRelationshipPath(entityField));
            })
            .count();
    
    return toManyConditionCount > 0; // ToMany 조건이 있으면 복잡한 것으로 판단
}
```

---

## 성능 최적화 가이드

### 1. 인덱스 설계

```sql
-- Phase 1 최적화를 위한 복합 인덱스
CREATE INDEX idx_post_search ON post (title, status, created_at, view_count, post_id);
CREATE INDEX idx_author_name ON author (name);
CREATE INDEX idx_tag_name ON tag (name);

-- 커서 기반 페이징을 위한 정렬 인덱스
CREATE INDEX idx_post_cursor ON post (created_at DESC, post_id ASC);
```

### 2. 배치 크기 설정

```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 100  # 2단계에서 나머지 ToMany 최적화
        jdbc:
          batch_size: 50               # JDBC 배치 크기
        order_inserts: true
        order_updates: true
```

### 3. 로그 설정

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql: TRACE
    dev.simplecore.searchable.core.service.specification.SearchableSpecificationBuilder: DEBUG
```

### 4. 성능 모니터링 포인트

- **Phase 1 성능**: ID 수집 쿼리의 실행 시간 확인
- **Phase 2 효율성**: MultipleBagFetchException 발생 여부
- **배치 로딩**: 나머지 ToMany 관계의 쿼리 개수
- **전체 처리 시간**: 2단계 합계 vs 기존 단일 쿼리
- **메모리 사용량**: HHH000104 경고 발생 여부

---

## 실제 사용 예시

### 복잡한 검색 조건

```java
SearchCondition condition = SearchCondition.builder()
    // 기본 필드 검색
    .filter("title", SearchOperator.CONTAINS, "Spring")
    .filter("status", SearchOperator.EQUALS, "PUBLISHED")
    
    // 관계형 필드 검색 (자동 JOIN)
    .filter("author.name", SearchOperator.CONTAINS, "John")
    .filter("tags.name", SearchOperator.EQUALS, "Java")
    
    // 복합 정렬 (Primary Key 자동 추가)
    .sort("createdAt", SortDirection.DESC)
    .sort("viewCount", SortDirection.ASC)
    
    // 페이징
    .page(0)
    .size(20)
    .build();

Page<Post> result = searchableService.findAllWithSearch(condition);
```

### 생성되는 SQL 분석 (2단계 쿼리)

**Phase 1: ID 수집 쿼리**
```sql
-- 효율적인 ID 수집 (Regular Join으로 HHH000104 방지)
SELECT DISTINCT p.post_id, p.created_at, p.view_count
FROM post p
LEFT JOIN author a ON p.author_id = a.id           -- Regular Join
LEFT JOIN post_tag pt ON p.post_id = pt.post_id    -- Regular Join
LEFT JOIN tag t ON pt.tag_id = t.id                -- Regular Join
WHERE p.title LIKE '%Spring%'
  AND p.status = 'PUBLISHED'
  AND LOWER(a.name) LIKE '%john%'
  AND t.name = 'Java'
ORDER BY p.created_at DESC, p.view_count ASC, p.post_id ASC  -- PK 자동 추가
LIMIT 20;  -- 데이터베이스에서 정상 적용
```

**Phase 2: 완전한 엔티티 로딩**
```sql
-- 메인 엔티티 + 첫 번째 ToMany (MultipleBagFetchException 방지)
SELECT DISTINCT p.post_id, p.title, p.status, p.created_at, p.view_count,
       a.author_id, a.name as author_name,
       c.comment_id, c.content, c.created_at as comment_created_at
FROM post p
LEFT JOIN FETCH author a ON p.author_id = a.id      -- ToOne: 항상 Fetch Join
LEFT JOIN FETCH comment c ON p.post_id = c.post_id  -- 첫 번째 ToMany만 Fetch Join
WHERE p.post_id IN (1, 5, 12, 18, 25, 33, 41, 47, 52, 58, 
                    63, 71, 78, 84, 91, 97, 103, 109, 115, 122)  -- Phase 1 결과
ORDER BY p.created_at DESC, p.view_count ASC, p.post_id ASC;

-- 나머지 ToMany는 배치 로딩으로 자동 처리
SELECT pt.post_id, t.tag_id, t.name
FROM post_tag pt
LEFT JOIN tag t ON pt.tag_id = t.id
WHERE pt.post_id IN (1, 5, 12, 18, 25, 33, 41, 47, 52, 58, 
                     63, 71, 78, 84, 91, 97, 103, 109, 115, 122);
```

**총 개수 쿼리 (필요시)**
```sql
SELECT COUNT(DISTINCT p.post_id)
FROM post p
LEFT JOIN author a ON p.author_id = a.id
LEFT JOIN post_tag pt ON p.post_id = pt.post_id
LEFT JOIN tag t ON pt.tag_id = t.id
WHERE p.title LIKE '%Spring%'
  AND p.status = 'PUBLISHED'
  AND LOWER(a.name) LIKE '%john%'
  AND t.name = 'Java';
```

---

**참고 문서:**
- [기본 사용법](basic-usage.md)
- [검색 연산자](search-operators.md)
- [커서 페이징](cursor-pagination.md)
- [API 레퍼런스](api-reference.md)

---

이러한 2단계 쿼리 전략을 통해 searchable-jpa는 복잡한 ToMany 관계에서도 효율적이고 안정적인 검색 기능을 제공합니다.

---

## 실제 애플리케이션 분석 사례

### 실행 로그 분석 결과

실제 프로덕션 환경에서 searchable-jpa를 적용한 결과를 분석했습니다:

#### **성공적으로 작동하는 기능들**

**1. 자동 Primary Key 정렬**
```
DEBUG SearchableSpecificationBuilder : Automatically added primary key field 'userId' to sort criteria for cursor-based pagination uniqueness
```
- `userId`가 자동으로 정렬 기준에 추가됨
- 동일한 `createdAt` 값으로 인한 레코드 누락 방지

**2. 커서 기반 페이징**
```sql
-- 첫 번째 페이지: 기본 정렬
ORDER BY created_at DESC, user_id ASC LIMIT ? OFFSET ?

-- 두 번째 페이지: 커서 기반
WHERE created_at < ? OR (created_at = ? AND user_id > ?)
ORDER BY created_at DESC, user_id ASC LIMIT ?
```
- OFFSET 제거로 성능 최적화
- 복합 조건으로 정확한 페이징

**3. ManyToMany 배치 로딩**
```sql
-- 10개씩 배치로 효율적 조회
WHERE user_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```
- `organizations`와 `roles` 관계 최적화
- N+1 문제 해결

#### **발견된 문제점과 해결책**

**1. ManyToOne 관계 N+1 문제**

**문제 상황:**
```sql
-- position 조회가 각각 개별적으로 실행됨
SELECT * FROM user_position WHERE position_id = ?  -- 반복 실행
```

**해결책 1: EntityGraph 사용**
```java
@EntityGraph(attributePaths = {"position"})
@SearchableField(entityField = "position.name")
private String positionName;
```

**해결책 2: DTO 프로젝션**
```java
// UserAccountSearchDTO에 추가
@SearchableField(entityField = "position.name")
private String positionName;  // 직접 필드로 조회

@SearchableField(entityField = "position.id") 
private String positionId;    // ID만 필요한 경우
```

**해결책 3: 배치 크기 설정**
```yaml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 10  # ManyToOne도 배치 로딩
```

**2. 쿼리 수 최적화**

**현재 상황:**
- 메인 쿼리: 2개 (데이터 + 카운트)
- 배치 쿼리: 4개 (organizations × 2, roles × 2) 
- N+1 쿼리: 10+개 (position 개별 조회)
- **총 16+개 쿼리**

**최적화 후 예상:**
- 메인 쿼리: 2개
- 배치 쿼리: 5개 (organizations × 2, roles × 2, position × 1)
- **총 7개 쿼리 (57% 감소)**

### 성능 개선 가이드

#### **1. 즉시 적용 가능한 설정**

```yaml
# application.yml
spring:
  jpa:
    properties:
      hibernate:
        default_batch_fetch_size: 10    # 배치 로딩 크기
        jdbc:
          batch_size: 20                # JDBC 배치 크기
        order_inserts: true
        order_updates: true
        format_sql: true                # SQL 포맷팅 (개발환경)
    show-sql: false                     # 프로덕션에서는 false
    
logging:
  level:
    org.hibernate.SQL: DEBUG            # 개발환경에서만
    org.hibernate.type.descriptor.sql: TRACE  # 파라미터 확인용
```

#### **2. 인덱스 최적화**

```sql
-- 커서 기반 페이징 최적화
CREATE INDEX idx_user_account_cursor ON user_account (created_at DESC, user_id ASC);

-- 검색 조건 최적화 (실제 사용되는 필드들)
CREATE INDEX idx_user_account_search ON user_account (enabled, created_at, user_id);

-- 외래키 인덱스
CREATE INDEX idx_user_account_position ON user_account (position_id);
```

#### **3. DTO 프로젝션 활용**

**현재 Entity 조회 방식:**
```java
// 전체 UserAccount 엔티티 조회 (모든 필드 + 관계들)
Page<UserAccount> users = searchableService.findAllWithSearch(condition);
```

**최적화된 DTO 방식:**
```java
// 필요한 필드만 조회
@SearchableField(entityField = "username")
private String username;

@SearchableField(entityField = "realName") 
private String realName;

@SearchableField(entityField = "position.name")  // JOIN하지만 필드만
private String positionName;

@SearchableField(entityField = "enabled")
private Boolean enabled;

// 컬렉션은 개수나 대표값만
@SearchableField(entityField = "organizations.size()")
private Integer organizationCount;

@SearchableField(entityField = "roles.size()")
private Integer roleCount;
```

### 모니터링 포인트

#### **1. 쿼리 수 모니터링**
```java
// 개발환경에서 쿼리 수 확인
@Component
public class QueryCountInterceptor implements Interceptor {
    private static final ThreadLocal<Integer> queryCount = new ThreadLocal<>();
    
    @Override
    public boolean onLoad(Object entity, Serializable id, Object[] state, String[] propertyNames, Type[] types) {
        incrementCount();
        return false;
    }
    
    // 요청 완료 후 로그 출력
    public void logQueryCount() {
        log.info("Total queries executed: {}", queryCount.get());
    }
}
```

#### **2. 성능 메트릭**
- **응답 시간**: 첫 페이지 vs 깊은 페이지
- **쿼리 수**: 요청당 실행되는 쿼리 개수
- **메모리 사용량**: 엔티티 로딩으로 인한 메모리 증가
- **데이터베이스 부하**: 커넥션 풀 사용률

#### **3. 알람 설정**
```yaml
# 성능 임계값 설정
searchable:
  monitoring:
    max-queries-per-request: 10      # 요청당 최대 쿼리 수
    max-response-time: 500           # 최대 응답 시간 (ms)
    enable-query-logging: true       # 쿼리 로깅 활성화
```

### 실제 개선 효과 예측

**Before (현재):**
- 쿼리 수: 16+개
- 응답 시간: ~50ms (작은 데이터셋)
- N+1 문제: position 관계에서 발생

**After (최적화 후):**
- 쿼리 수: 7개 (57% 감소)
- 응답 시간: ~30ms (40% 개선)
- N+1 문제: 해결

**대용량 데이터에서의 효과:**
- 10,000+ 레코드: 커서 기반 페이징으로 일정한 성능 유지
- 깊은 페이지: OFFSET 제거로 성능 저하 없음
- 메모리 안정성: DTO 프로젝션으로 메모리 사용량 감소

---

**참고 문서:**

## 8. ManyToMany N+1 문제 완전 해결

### 8.1 자동 감지 및 배치 로딩

searchable-jpa는 **ManyToMany 관계를 자동으로 감지**하고 배치 로딩을 적용합니다:

```java
// 라이브러리가 자동으로 수행하는 작업
DetectManyToManyFields: Found ManyToMany relationship: organizations
DetectManyToManyFields: Found ManyToMany relationship: roles
Phase 2: Configuring batch loading for ManyToMany relationships: [organizations, roles]
```

### 8.2 추가 최적화: @BatchSize 어노테이션

**완전한 N+1 해결**을 위해 엔티티에 `@BatchSize` 어노테이션을 추가하세요:

```java
@Entity
public class UserAccount {
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_account_organizations")
    @BatchSize(size = 25)  // 25개씩 배치 로딩
    private Set<UserOrganization> organizations = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY) 
    @JoinTable(name = "user_account_roles")
    @BatchSize(size = 25)  // 25개씩 배치 로딩
    private Set<UserRole> roles = new HashSet<>();
}
```

### 8.3 성능 개선 효과

**Before (N+1 문제):**
```sql
-- 메인 쿼리: 10개 UserAccount 조회
SELECT * FROM user_account ORDER BY created_at DESC LIMIT 10;

-- N+1 문제: 각 Organization마다 개별 쿼리 (5개 추가 쿼리)
SELECT * FROM user_organization WHERE organization_id = ?;
SELECT * FROM user_organization WHERE organization_id = ?;
SELECT * FROM user_organization WHERE organization_id = ?;
SELECT * FROM user_organization WHERE organization_id = ?;
SELECT * FROM user_organization WHERE organization_id = ?;

-- 총 쿼리 수: 16개 (1 + 10 + 5)
```

**After (배치 로딩):**
```sql
-- 메인 쿼리: 10개 UserAccount 조회
SELECT * FROM user_account ORDER BY created_at DESC LIMIT 10;

-- 배치 로딩: 한 번에 모든 관계 조회
SELECT * FROM user_account_organizations WHERE user_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
SELECT * FROM user_account_roles WHERE user_id IN (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

-- 총 쿼리 수: 3개 (1 + 1 + 1)
```

### 8.4 배치 사이즈 권장값

| 데이터 크기 | 권장 배치 사이즈 | 설명 |
|------------|------------------|------|
| 소규모 (< 100개) | `@BatchSize(size = 10)` | 메모리 효율성 우선 |
| 중규모 (100-1000개) | `@BatchSize(size = 25)` | **권장값** - 균형점 |
| 대규모 (> 1000개) | `@BatchSize(size = 50)` | 성능 우선 |

### 8.5 실제 적용 예시

```java
@Entity
@Table(name = "user_account")
public class UserAccount {
    
    @Id
    private String userId;
    
    // ManyToOne은 자동으로 Fetch Join 적용됨
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    private UserPosition position;
    
    // ManyToMany는 @BatchSize로 최적화
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_account_organizations",
               joinColumns = @JoinColumn(name = "user_id"),
               inverseJoinColumns = @JoinColumn(name = "organization_id"))
    @BatchSize(size = 25)
    private Set<UserOrganization> organizations = new HashSet<>();
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_account_roles",
               joinColumns = @JoinColumn(name = "user_id"), 
               inverseJoinColumns = @JoinColumn(name = "role_id"))
    @BatchSize(size = 25)
    private Set<UserRole> roles = new HashSet<>();
}
```

### 8.6 성능 모니터링

라이브러리가 자동으로 생성하는 로그를 통해 최적화 상태를 확인할 수 있습니다:

```log
성공적인 최적화:
ConfigureBatchLoadingForSession: Detected ManyToMany relationships: [organizations, roles]
ConfigureBatchLoadingForSession: Batch loading is already enabled by Hibernate

배치 쿼리 실행:
where organizati0_.user_id in (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
where roles0_.user_id in (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
```