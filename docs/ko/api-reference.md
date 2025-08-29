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
| `sortField` | String | `""` | 정렬 시 사용할 필드명. 비어있으면 entityField 또는 필드명 사용 |

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
    private final List<Node> conditions;
    private Sort sort;
    private Integer page;
    private Integer size;
}
```

#### 주요 메서드

| 메서드 | 반환 타입 | 설명 |
|--------|-----------|------|
| `getConditions()` | `List<Node>` | 검색 조건 노드 목록 반환 |
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
    @NonNull
    Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    @NonNull
    <D> Page<D> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<D> dtoClass);
    
    @NonNull
    Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    @NonNull
    Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    // 집계 메서드
    long countWithSearch(@NonNull SearchCondition<?> searchCondition);
    boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition);

    // 수정/삭제 메서드
    long deleteWithSearch(@NonNull SearchCondition<?> searchCondition);
    long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData);
}
```

### DefaultSearchableService<T, ID>

`SearchableService`의 기본 구현체입니다.

```java
public class DefaultSearchableService<T, ID> implements SearchableService<T> {
    
    // 생성자
    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager)
    
    // 내부적으로 2단계 쿼리 최적화를 사용하는 기본 메서드들
    // 클라이언트는 기존 방식대로 사용하면서 2단계 쿼리 최적화의 성능 이점을 얻음
}
```

## 빌더 클래스

### SearchConditionBuilder

프로그래매틱하게 검색 조건을 생성하는 빌더입니다.

```java
public class SearchConditionBuilder<D> {
    public static <D> SearchConditionBuilder<D> create(Class<D> dtoClass)
    
    public SearchConditionBuilder<D> where(Consumer<ConditionGroupBuilder> consumer)
    public SearchConditionBuilder<D> and(Consumer<ConditionGroupBuilder> consumer)
    public SearchConditionBuilder<D> or(Consumer<ConditionGroupBuilder> consumer)
    public ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer)
    public ChainedSearchCondition<D> page(int page)
    public ChainedSearchCondition<D> size(int size)
    public SearchCondition<D> build()
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
    ChainedCondition contains(String field, String value);
    ChainedCondition notContains(String field, String value);
    ChainedCondition startsWith(String field, String value);
    ChainedCondition notStartsWith(String field, String value);
    ChainedCondition endsWith(String field, String value);
    ChainedCondition notEndsWith(String field, String value);
    
    // NULL 체크 연산자
    ChainedCondition isNull(String field);
    ChainedCondition isNotNull(String field);
    
    // 컬렉션 연산자
    ChainedCondition in(String field, List<?> values);
    ChainedCondition notIn(String field, List<?> values);
    
    // 범위 연산자
    ChainedCondition between(String field, Object start, Object end);
    ChainedCondition notBetween(String field, Object start, Object end);
    
    // 그룹 조건
    ChainedCondition where(Consumer<FirstCondition> consumer);
    ChainedCondition and(Consumer<FirstCondition> consumer);
    ChainedCondition or(Consumer<FirstCondition> consumer);
}
```

### ChainedCondition

체이닝된 조건을 정의하는 인터페이스입니다.

```java
public interface ChainedCondition extends FirstCondition {
    // FirstCondition의 모든 메서드를 상속받아 체이닝 가능
    // 추가로 OR 연산자들 제공
    ChainedCondition orEquals(String field, Object value);
    ChainedCondition orNotEquals(String field, Object value);
    ChainedCondition orGreaterThan(String field, Object value);
    ChainedCondition orGreaterThanOrEqualTo(String field, Object value);
    ChainedCondition orLessThan(String field, Object value);
    ChainedCondition orLessThanOrEqualTo(String field, Object value);
    ChainedCondition orContains(String field, String value);
    ChainedCondition orNotContains(String field, String value);
    ChainedCondition orStartsWith(String field, String value);
    ChainedCondition orNotStartsWith(String field, String value);
    ChainedCondition orEndsWith(String field, String value);
    ChainedCondition orNotEndsWith(String field, String value);
    ChainedCondition orIsNull(String field);
    ChainedCondition orIsNotNull(String field);
    ChainedCondition orIn(String field, List<?> values);
    ChainedCondition orNotIn(String field, List<?> values);
    ChainedCondition orBetween(String field, Object start, Object end);
    ChainedCondition orNotBetween(String field, Object start, Object end);
}
```

### SortBuilder

정렬 조건을 구성하는 빌더입니다.

```java
public interface SortBuilder {
    SortBuilder asc(String field);
    SortBuilder desc(String field);
}
```

## 파서 클래스

### SearchableParamsParser<D>

쿼리 파라미터를 `SearchCondition`으로 변환하는 파서입니다.

```java
public class SearchableParamsParser<D> {
    public SearchableParamsParser(Class<D> dtoClass)
    
    public SearchCondition<D> convert(Map<String, String> params)
}
```

#### 지원하는 파라미터 형식

```java
// 기본 검색
"field.operator=value"

// 정렬
"sort=field.asc,field.desc"

// 페이징
"page=0&size=10"

// 범위 검색
"field.between=value1,value2"

// IN 검색
"field.in=value1,value2,value3"
```

## 2단계 쿼리 최적화

### 자동 최적화

Searchable JPA는 내부적으로 2단계 쿼리 최적화를 사용하여 성능을 최적화합니다. 클라이언트는 표준 Spring Data의 `Page<T>` 인터페이스를 사용하면서 2단계 쿼리 최적화의 이점을 얻을 수 있습니다.

```java
// 클라이언트 코드는 기존과 동일
Page<Post> result = postService.findAllWithSearch(condition);

// 내부적으로는 2단계 쿼리 최적화로 변환되어 실행됨
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

### SearchableJoinException

조인 관련 오류 시 발생하는 예외입니다.

```java
public class SearchableJoinException extends SearchableException {
    public SearchableJoinException(String message)
}
```

### SearchableOperationException

작업 실행 중 오류가 발생할 때 사용하는 예외입니다.

```java
public class SearchableOperationException extends SearchableException {
    public SearchableOperationException(String message)
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
@ConfigurationProperties(prefix = "searchable")
public class SearchableProperties {
    private SwaggerProperties swagger = new SwaggerProperties();
    private HibernateProperties hibernate = new HibernateProperties();

    @Data
    public static class SwaggerProperties {
        private boolean enabled = true;
    }

    @Data
    public static class HibernateProperties {
        private boolean autoOptimization = true;
        private int defaultBatchFetchSize = 100;
        private int jdbcBatchSize = 1000;
        private boolean batchVersionedData = true;
        private boolean orderInserts = true;
        private boolean orderUpdates = true;
        private boolean inClauseParameterPadding = true;
    }
}
```

## 예제 코드

### 기본 사용 예제

> **상세한 사용 예제**: [기본 사용법](basic-usage.md) 문서에서 완전한 예제 코드를 확인할 수 있습니다.

#### DTO 클래스 정의
```java
// 기본적인 DTO 설정 예제는 기본 사용법 문서 참조
// 복합 키 관련 예제는 고급 기능 문서 참조
```

#### 서비스 클래스 구현
```java
// 서비스 구현 예제는 기본 사용법 문서 참조
// 고급 기능은 고급 기능 문서 참조
```

#### 컨트롤러 구현
```java
// 컨트롤러 구현 예제는 기본 사용법 문서 참조
// OpenAPI 통합은 OpenAPI 통합 문서 참조
```

## 다음 단계

- [FAQ](faq.md) - 자주 묻는 질문들

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: OpenAPI 통합](openapi-integration.md) | [다음: FAQ](faq.md) 