# API 레퍼런스

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: OpenAPI 통합](openapi-integration.md) | [다음: FAQ](faq.md)

---

이 문서는 Searchable JPA의 모든 API와 클래스에 대한 상세한 레퍼런스를 제공합니다.

## 핵심 어노테이션

### @SearchableField

검색 가능한 필드를 정의하는 어노테이션입니다.

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableField {
    String entityField() default "";
    SearchOperator[] operators() default {};
    boolean sortable() default false;
}
```

#### 속성

| 속성 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `entityField` | String | `""` | 엔티티의 실제 필드명. 비어있으면 DTO 필드명 사용 |
| `operators` | SearchOperator[] | `{}` | 허용할 검색 연산자 배열. 비어있으면 모든 연산자 허용 |
| `sortable` | boolean | `false` | 정렬 가능 여부 |

#### 사용 예제

```java
public class UserSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String name;
    
    @SearchableField(entityField = "profile.email", operators = {EQUALS, ENDS_WITH})
    private String email;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN})
    private Integer age;
}
```

### @SearchableParams

GET 방식 검색 파라미터에 대한 OpenAPI 문서를 자동 생성하는 어노테이션입니다.

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableParams {
    Class<?> value();
}
```

#### 사용 예제

```java
@GetMapping("/search")
public Page<User> search(
    @RequestParam @SearchableParams(UserSearchDTO.class) Map<String, String> params
) {
    // ...
}
```

## 핵심 클래스

### SearchCondition<D>

검색 조건을 정의하는 핵심 클래스입니다.

```java
public class SearchCondition<D> {
    private final List<Node> nodes;
    private Sort sort;
    private Integer page;
    private Integer size;
}
```

#### 주요 메서드

| 메서드 | 반환 타입 | 설명 |
|--------|-----------|------|
| `getNodes()` | `List<Node>` | 검색 조건 노드 목록 반환 |
| `getSort()` | `Sort` | 정렬 조건 반환 |
| `getPage()` | `Integer` | 페이지 번호 반환 |
| `getSize()` | `Integer` | 페이지 크기 반환 |
| `setSort(Sort)` | `void` | 정렬 조건 설정 |
| `setPage(Integer)` | `void` | 페이지 번호 설정 |
| `setSize(Integer)` | `void` | 페이지 크기 설정 |

#### 정적 메서드

```java
// JSON에서 SearchCondition 생성
public static <T> SearchCondition<T> fromJson(String json, Class<T> dtoClass)

// SearchCondition을 JSON으로 변환
public String toJson()
```

### SearchCondition.Condition

개별 검색 조건을 나타내는 클래스입니다.

```java
public static class Condition implements ConditionNode {
    private LogicalOperator operator;
    private final String field;
    private final SearchOperator searchOperator;
    private final Object value;
    private final Object value2;
    private String entityField;
}
```

#### 생성자

```java
public Condition(String field, SearchOperator searchOperator, Object value)
public Condition(String field, SearchOperator searchOperator, Object value, Object value2)
public Condition(LogicalOperator operator, String field, SearchOperator searchOperator, Object value)
```

### SearchCondition.Group

조건 그룹을 나타내는 클래스입니다.

```java
public static class Group implements GroupNode {
    private LogicalOperator operator;
    private final List<Node> nodes;
}
```

### SearchCondition.Sort

정렬 조건을 정의하는 클래스입니다.

```java
public static class Sort {
    private final List<Order> orders;
    
    public void addOrder(Order order)
    public void addOrder(String field, Direction direction)
}
```

### SearchCondition.Order

개별 정렬 조건을 나타내는 클래스입니다.

```java
public static class Order {
    private final String field;
    private final Direction direction;
    private final String entityField;
    
    public boolean isAscending()
    public boolean isDescending()
}
```

## 검색 연산자

### SearchOperator

모든 검색 연산자를 정의하는 열거형입니다.

```java
public enum SearchOperator {
    // 비교 연산자
    EQUALS("equals"),
    NOT_EQUALS("notEquals"),
    GREATER_THAN("greaterThan"),
    GREATER_THAN_OR_EQUAL_TO("greaterThanOrEqualTo"),
    LESS_THAN("lessThan"),
    LESS_THAN_OR_EQUAL_TO("lessThanOrEqualTo"),
    
    // 문자열 패턴 연산자
    CONTAINS("contains"),
    NOT_CONTAINS("notContains"),
    STARTS_WITH("startsWith"),
    NOT_STARTS_WITH("notStartsWith"),
    ENDS_WITH("endsWith"),
    NOT_ENDS_WITH("notEndsWith"),
    
    // NULL 체크 연산자
    IS_NULL("isNull"),
    IS_NOT_NULL("isNotNull"),
    
    // 컬렉션 연산자
    IN("in"),
    NOT_IN("notIn"),
    
    // 범위 연산자
    BETWEEN("between"),
    NOT_BETWEEN("notBetween");
}
```

#### 주요 메서드

```java
public String getName()                    // 연산자 이름 반환
public static SearchOperator fromName(String operator)  // 이름으로 연산자 찾기
```

### LogicalOperator

논리 연산자를 정의하는 열거형입니다.

```java
public enum LogicalOperator {
    AND("and"),
    OR("or");
    
    public String getName()
    public static LogicalOperator fromName(String operator)
}
```

## 서비스 인터페이스

### SearchableService<T>

검색 기능을 제공하는 핵심 서비스 인터페이스입니다.

```java
public interface SearchableService<T> {
    // 검색 메서드
    Page<T> findAllWithSearch(SearchCondition<?> searchCondition);
    <D> Page<D> findAllWithSearch(SearchCondition<?> searchCondition, Class<D> dtoClass);
    Optional<T> findOneWithSearch(SearchCondition<?> searchCondition);
    Optional<T> findFirstWithSearch(SearchCondition<?> searchCondition);
    
    // 집계 메서드
    long countWithSearch(SearchCondition<?> searchCondition);
    boolean existsWithSearch(SearchCondition<?> searchCondition);
    
    // 수정/삭제 메서드
    long updateWithSearch(SearchCondition<?> searchCondition, Object updateData);
    long deleteWithSearch(SearchCondition<?> searchCondition);
}
```

### DefaultSearchableService<T>

`SearchableService`의 기본 구현체입니다.

```java
public class DefaultSearchableService<T> implements SearchableService<T> {
    
    // 생성자
    public DefaultSearchableService(JpaRepository<T, ?> repository, Class<T> entityClass)
    public DefaultSearchableService(JpaRepository<T, ?> repository, EntityManager entityManager)
    
    // 내부적으로 커서 기반 페이징을 사용하는 기본 메서드들
    // 클라이언트는 기존 방식대로 사용하면서 커서 페이징의 성능 이점을 얻음
}
```

## 빌더 클래스

### SearchConditionBuilder

프로그래매틱하게 검색 조건을 생성하는 빌더입니다.

```java
public class SearchConditionBuilder {
    public static <D> ChainedSearchCondition<D> create(Class<D> dtoClass)
}
```

### ChainedSearchCondition<D>

체이닝 방식으로 검색 조건을 구성하는 인터페이스입니다.

```java
public interface ChainedSearchCondition<D> {
    ChainedSearchCondition<D> and(Consumer<FirstCondition> consumer);
    ChainedSearchCondition<D> or(Consumer<FirstCondition> consumer);
    ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer);
    ChainedSearchCondition<D> page(int page);
    ChainedSearchCondition<D> size(int size);
    SearchCondition<D> build();
}
```

### FirstCondition

첫 번째 조건을 정의하는 인터페이스입니다.

```java
public interface FirstCondition {
    // 비교 연산자
    ChainedCondition equals(String field, Object value);
    ChainedCondition notEquals(String field, Object value);
    ChainedCondition greaterThan(String field, Object value);
    ChainedCondition greaterThanOrEqualTo(String field, Object value);
    ChainedCondition lessThan(String field, Object value);
    ChainedCondition lessThanOrEqualTo(String field, Object value);
    
    // 문자열 패턴 연산자
    ChainedCondition contains(String field, Object value);
    ChainedCondition notContains(String field, Object value);
    ChainedCondition startsWith(String field, Object value);
    ChainedCondition notStartsWith(String field, Object value);
    ChainedCondition endsWith(String field, Object value);
    ChainedCondition notEndsWith(String field, Object value);
    
    // NULL 체크 연산자
    ChainedCondition isNull(String field);
    ChainedCondition isNotNull(String field);
    
    // 컬렉션 연산자
    ChainedCondition in(String field, Object... values);
    ChainedCondition in(String field, Collection<?> values);
    ChainedCondition notIn(String field, Object... values);
    ChainedCondition notIn(String field, Collection<?> values);
    
    // 범위 연산자
    ChainedCondition between(String field, Object from, Object to);
    ChainedCondition notBetween(String field, Object from, Object to);
}
```

### ChainedCondition

체이닝된 조건을 정의하는 인터페이스입니다.

```java
public interface ChainedCondition extends FirstCondition {
    // FirstCondition의 모든 메서드를 상속받아 체이닝 가능
}
```

### SortBuilder

정렬 조건을 구성하는 빌더입니다.

```java
public interface SortBuilder {
    SortBuilder asc(String field);
    SortBuilder desc(String field);
    SortBuilder asc(String field, String entityField);
    SortBuilder desc(String field, String entityField);
}
```

## 파서 클래스

### SearchableParamsParser<D>

쿼리 파라미터를 `SearchCondition`으로 변환하는 파서입니다.

```java
public class SearchableParamsParser<D> {
    public SearchableParamsParser(Class<D> dtoClass)
    
    public SearchCondition<D> convert(Map<String, String> params)
    public SearchCondition<D> convert(MultiValueMap<String, String> params)
}
```

#### 지원하는 파라미터 형식

```java
// 기본 검색
"field.operator=value"

// 정렬
"sort=field,direction"

// 페이징
"page=0&size=10"

// 범위 검색
"field.between=value1,value2"

// IN 검색
"field.in=value1,value2,value3"
```

## 커서 페이징

### 내부 구현

Searchable JPA는 내부적으로 커서 기반 페이징을 사용하여 성능을 최적화합니다. 클라이언트는 표준 Spring Data의 `Page<T>` 인터페이스를 사용하면서 커서 페이징의 이점을 얻을 수 있습니다.

```java
// 표준 Spring Data Page 인터페이스 사용
Page<Post> result = searchableService.findAllWithSearch(condition);

// 내부적으로는 커서 기반 쿼리로 변환되어 실행됨
// 클라이언트 코드 변경 없이 성능 향상
```

## 예외 클래스

### SearchableException

Searchable JPA의 기본 예외 클래스입니다.

```java
public class SearchableException extends RuntimeException {
    public SearchableException(String message)
    public SearchableException(String message, Throwable cause)
}
```

### SearchableValidationException

검증 실패 시 발생하는 예외입니다.

```java
public class SearchableValidationException extends SearchableException {
    private final List<String> validationErrors;
    
    public List<String> getValidationErrors()
}
```

### SearchableParseException

파싱 실패 시 발생하는 예외입니다.

```java
public class SearchableParseException extends SearchableException {
    private final String invalidValue;
    private final String fieldName;
    
    public String getInvalidValue()
    public String getFieldName()
}
```

### SearchableConfigurationException

설정 오류 시 발생하는 예외입니다.

```java
public class SearchableConfigurationException extends SearchableException {
    public SearchableConfigurationException(String message)
}
```

## 유틸리티 클래스

### SearchableFieldUtils

`@SearchableField` 어노테이션 처리를 위한 유틸리티입니다.

```java
public class SearchableFieldUtils {
    public static boolean isSearchableField(Field field)
    public static SearchableField getSearchableField(Field field)
    public static String getEntityFieldName(Field field, SearchableField annotation)
    public static Set<SearchOperator> getAllowedOperators(SearchableField annotation)
    public static boolean isSortable(SearchableField annotation)
}
```

### SearchableValueParser

값 변환을 위한 유틸리티입니다.

```java
public class SearchableValueParser {
    public static Object parseValue(String value, Class<?> targetType)
    public static List<Object> parseValues(String value, Class<?> targetType)
    public static Object[] parseBetweenValues(String value, Class<?> targetType)
}
```

## 설정 클래스

### SearchableProperties

Searchable JPA의 설정 속성을 정의하는 클래스입니다.

```java
@ConfigurationProperties(prefix = "searchable.jpa")
public class SearchableProperties {
    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private boolean enableCursorPagination = true;
    
    // getters and setters
}
```

## 사용 예제

### 기본 사용법

```java
// 1. DTO 정의
public class PostSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String title;
    
    @SearchableField(operators = {EQUALS, IN})
    private PostStatus status;
}

// 2. 서비스 구현
@Service
public class PostService extends DefaultSearchableService<Post> {
    public PostService(PostRepository repository) {
        super(repository, Post.class);
    }
}

// 3. 컨트롤러 구현
@RestController
public class PostController {
    
    @GetMapping("/search")
    public Page<Post> search(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
    ) {
        SearchCondition<PostSearchDTO> condition = 
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return postService.findAllWithSearch(condition);
    }
    
    @PostMapping("/search")
    public Page<Post> searchPost(@RequestBody SearchCondition<PostSearchDTO> condition) {
        return postService.findAllWithSearch(condition);
    }
}
```

### 빌더 사용법

```java
SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
    .create(PostSearchDTO.class)
    .where(group -> group
        .contains("title", "Spring")
        .equals("status", PostStatus.PUBLISHED)
    )
    .sort(sort -> sort
        .desc("createdAt")
        .asc("title")
    )
    .page(0)
    .size(10)
    .build();
```

## 다음 단계

- [FAQ](faq.md) - 자주 묻는 질문들

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: OpenAPI 통합](openapi-integration.md) | [다음: FAQ](faq.md) 