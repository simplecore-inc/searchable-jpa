# API 레퍼런스

이 문서는 Searchable JPA의 모든 API와 클래스를 상세히 설명합니다.

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
    String sortField() default "";
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

GET 방식 검색 파라미터의 OpenAPI 문서를 자동으로 생성하는 어노테이션입니다.

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
    private Set<String> fetchFields;
}
```

`@JsonProperty("conditions")`는 `nodes` 필드를 JSON에서 `conditions` 키로 노출하지만, Java 필드명과 게터는 각각 `nodes`, `getNodes()`입니다. `fetchFields`는 지연 로딩 관계를 명시적으로 fetch join하기 위한 서버 전용 속성이며, JSON 역직렬화 시에는 무시됩니다.

#### 주요 메서드

| 메서드 | 반환 타입 | 설명 |
|--------|-----------|------|
| `getNodes()` | `List<Node>` | 검색 조건 노드 목록 반환 |
| `getSort()` | `Sort` | 정렬 조건 반환 |
| `getPage()` | `Integer` | 페이지 번호 반환 |
| `getSize()` | `Integer` | 페이지 크기 반환 |
| `getFetchFields()` | `Set<String>` | fetch join 대상 엔티티 필드 목록 반환 |
| `setSort(Sort)` | `void` | 정렬 조건 설정 |
| `setPage(Integer)` | `void` | 페이지 번호 설정 |
| `setSize(Integer)` | `void` | 페이지 크기 설정 |
| `setFetchFields(Set<String>)` | `void` | fetch join 대상 엔티티 필드 목록 설정 |

#### 정적 메서드

```java
// Creates a SearchCondition from a JSON string
public static <T> SearchCondition<T> fromJson(String json, Class<T> dtoClass)

// Converts this SearchCondition to a JSON string
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
public Condition(String field, SearchOperator searchOperator, Object value, Object value2)
public Condition(LogicalOperator operator, String field, SearchOperator searchOperator, Object value, Object value2, String entityField)
```

4개 인자 생성자는 `operator`와 `entityField`를 비워둔 채로 조건을 생성하며, 이후 `setOperator()`/`setEntityField()`로 채울 수 있습니다. 6개 인자 생성자는 Jackson 역직렬화(`@JsonCreator`)와 내부 복사 로직에서 모든 필드를 한 번에 지정할 때 사용합니다.

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
}
```

### SearchCondition.Order

개별 정렬 조건을 나타내는 클래스입니다.

```java
public static class Order {
    private final String field;
    private final Direction direction;
    private final String entityField;
    
    public Order(String field, Direction direction, String entityField)
    public Order(String field, Direction direction)
    
    public boolean isAscending()
}
```

## 검색 연산자

### SearchOperator

모든 검색 연산자를 정의하는 열거형입니다.

```java
public enum SearchOperator {
    // Comparison operators
    EQUALS("equals"),
    NOT_EQUALS("notEquals"),
    GREATER_THAN("greaterThan"),
    GREATER_THAN_OR_EQUAL_TO("greaterThanOrEqualTo"),
    LESS_THAN("lessThan"),
    LESS_THAN_OR_EQUAL_TO("lessThanOrEqualTo"),
    
    // String pattern operators
    CONTAINS("contains"),
    NOT_CONTAINS("notContains"),
    STARTS_WITH("startsWith"),
    NOT_STARTS_WITH("notStartsWith"),
    ENDS_WITH("endsWith"),
    NOT_ENDS_WITH("notEndsWith"),
    
    // NULL check operators
    IS_NULL("isNull"),
    IS_NOT_NULL("isNotNull"),
    
    // Collection operators
    IN("in"),
    NOT_IN("notIn"),
    
    // Range operators
    BETWEEN("between"),
    NOT_BETWEEN("notBetween");
}
```

#### 주요 메서드

```java
public String getName()                                 // Returns the operator name
public static SearchOperator fromName(String operator)   // Finds an operator by name
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
    // Search methods
    @NonNull
    Page<T> findAllWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    @NonNull
    <P> Page<P> findAllWithSearch(@NonNull SearchCondition<?> searchCondition, Class<P> projectionClass);
    
    @NonNull
    Optional<T> findOneWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    @NonNull
    Optional<T> findFirstWithSearch(@NonNull SearchCondition<?> searchCondition);
    
    // Aggregate methods
    long countWithSearch(@NonNull SearchCondition<?> searchCondition);
    boolean existsWithSearch(@NonNull SearchCondition<?> searchCondition);

    // Update/delete methods
    long deleteWithSearch(@NonNull SearchCondition<?> searchCondition);
    long updateWithSearch(@NonNull SearchCondition<?> searchCondition, @NonNull Object updateData);
}
```

`findAllWithSearch(SearchCondition<?>, Class<P>)`의 `projectionClass`는 반드시 Spring Data 프로젝션 **인터페이스**여야 합니다. 구체 클래스를 전달하면 `SearchableConfigurationException("Projection class must be an interface")`이 발생합니다.

`findOneWithSearch`는 조건에 맞는 엔티티가 2건 이상이면 `NonUniqueResultException`을 던집니다.

### DefaultSearchableService<T, ID>

`SearchableService`의 기본 구현체입니다. `DefaultSearchableService`를 직접 상속해 사용합니다.

```java
public class DefaultSearchableService<T, ID> implements SearchableServiceSupport<T, ID> {

    // Constructor
    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager)

    // Returns the delegate that implements all SearchableService methods
    @Override
    public SearchableServiceDelegate<T, ID> getSearchableDelegate()
}
```

`SearchableService`의 모든 메서드는 내부적으로 `SearchableServiceDelegate` 인스턴스에 위임되어 실행됩니다. 서비스 클래스는 기존 방식대로 `DefaultSearchableService`를 상속하기만 하면 2단계 쿼리 최적화 효과를 그대로 얻습니다.

### SearchableServiceSupport<T, ID>

`DefaultSearchableService`를 상속할 수 없는 경우(이미 다른 클래스를 상속하는 경우 등)를 위한 믹스인 인터페이스입니다. `SearchableService`의 모든 메서드를 `SearchableServiceDelegate`에 위임하는 기본 메서드로 제공합니다.

```java
public interface SearchableServiceSupport<T, ID> extends SearchableService<T> {

    // Returns the delegate that provides the actual SearchableService implementation
    SearchableServiceDelegate<T, ID> getSearchableDelegate();
}
```

#### 사용 예제

```java
public class MyService extends SomeOtherBaseClass
        implements SearchableServiceSupport<MyEntity, Long> {

    private final SearchableServiceDelegate<MyEntity, Long> searchableDelegate;

    public MyService(JpaRepository<MyEntity, Long> repository, EntityManager em) {
        this.searchableDelegate = new SearchableServiceDelegate<>(repository, em, MyEntity.class);
    }

    @Override
    public SearchableServiceDelegate<MyEntity, Long> getSearchableDelegate() {
        return searchableDelegate;
    }
}
```

### SearchableServiceDelegate<T, ID>

`SearchableService`의 실제 구현 로직을 담고 있는 독립 실행형 위임체입니다. 인스턴스를 직접 생성해 메서드를 호출할 수도 있고, `SearchableServiceSupport`의 기본 메서드로 간접적으로 사용할 수도 있습니다. `DefaultSearchableService`도 내부적으로 이 클래스에 위임합니다.

```java
public class SearchableServiceDelegate<T, ID> implements SearchableService<T> {

    public SearchableServiceDelegate(JpaRepository<T, ID> repository, EntityManager entityManager, Class<T> entityClass)

    public SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition)
    public SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition)
}
```

## 빌더 클래스

### SearchConditionBuilder<D>

프로그래밍 방식으로 검색 조건을 생성하는 빌더입니다.

```java
public class SearchConditionBuilder<D> {
    public static <D> SearchConditionBuilder<D> create(Class<D> dtoClass)
    public static <D> SearchConditionBuilder<D> from(SearchCondition<D> existing, Class<D> dtoClass)
    
    public SearchConditionBuilder<D> where(Consumer<ConditionGroupBuilder> consumer)
    public SearchConditionBuilder<D> and(Consumer<ConditionGroupBuilder> consumer)
    public SearchConditionBuilder<D> or(Consumer<ConditionGroupBuilder> consumer)
    public ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer)
    public ChainedSearchCondition<D> page(int page)
    public ChainedSearchCondition<D> size(int size)
    public ChainedSearchCondition<D> fetchFields(String... fields)
    public ChainedSearchCondition<D> fetchFields(Set<String> fields)
    public SearchCondition<D> build()
    public Class<?> getDtoClass()
}
```

`from(existing, dtoClass)`는 기존 `SearchCondition`의 노드, 정렬, 페이징, fetch join 대상을 깊은 복사해 새 빌더를 초기화합니다. 원본 조건을 그대로 두고 조건을 추가로 확장할 때 사용합니다.

### ChainedSearchCondition<D>

체이닝 방식으로 검색 조건을 구성하는 인터페이스입니다.

```java
public interface ChainedSearchCondition<D> {
    ChainedSearchCondition<D> and(Consumer<FirstCondition> consumer);
    ChainedSearchCondition<D> or(Consumer<FirstCondition> consumer);
    ChainedSearchCondition<D> sort(Consumer<SortBuilder> consumer);
    ChainedSearchCondition<D> page(int page);
    ChainedSearchCondition<D> size(int size);
    ChainedSearchCondition<D> fetchFields(String... fields);
    ChainedSearchCondition<D> fetchFields(Set<String> fields);
    SearchCondition<D> build();
}
```

### FirstCondition

첫 번째 조건을 정의하는 인터페이스입니다.

```java
public interface FirstCondition {
    // Comparison operators
    ChainedCondition equals(String field, Object value);
    ChainedCondition notEquals(String field, Object value);
    ChainedCondition greaterThan(String field, Object value);
    ChainedCondition greaterThanOrEqualTo(String field, Object value);
    ChainedCondition lessThan(String field, Object value);
    ChainedCondition lessThanOrEqualTo(String field, Object value);
    
    // String pattern operators
    ChainedCondition contains(String field, String value);
    ChainedCondition notContains(String field, String value);
    ChainedCondition startsWith(String field, String value);
    ChainedCondition notStartsWith(String field, String value);
    ChainedCondition endsWith(String field, String value);
    ChainedCondition notEndsWith(String field, String value);
    
    // NULL check operators
    ChainedCondition isNull(String field);
    ChainedCondition isNotNull(String field);
    
    // Collection operators
    ChainedCondition in(String field, List<?> values);
    ChainedCondition notIn(String field, List<?> values);
    
    // Range operators
    ChainedCondition between(String field, Object start, Object end);
    ChainedCondition notBetween(String field, Object start, Object end);

    // Group condition
    ChainedCondition where(Consumer<FirstCondition> consumer);
}
```

### ChainedCondition

체이닝된 조건을 정의하는 인터페이스입니다.

```java
public interface ChainedCondition extends FirstCondition {
    // Inherits all FirstCondition methods for chaining
    // Group conditions (and/or)
    ChainedCondition and(Consumer<FirstCondition> consumer);
    ChainedCondition or(Consumer<FirstCondition> consumer);

    // Additional OR operator variants
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

정렬 조건을 구성하는 빌더 클래스입니다.

```java
public class SortBuilder {
    public SortBuilder asc(String field);
    public SortBuilder desc(String field);
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
// Basic search
"field.operator=value"

// Sort
"sort=field.asc,field.desc"

// Pagination
"page=0&size=10"

// Range search
"field.between=value1,value2"

// IN search
"field.in=value1,value2,value3"
```

## 2단계 쿼리 최적화

### 자동 최적화

Searchable JPA는 내부적으로 2단계 쿼리 기법을 사용해 성능을 높입니다. 표준 Spring Data `Page<T>` 인터페이스를 그대로 사용하면서도 2단계 쿼리 최적화 효과를 얻습니다.

```java
// Client code stays the same as before
Page<Post> result = postService.findAllWithSearch(condition);

// Internally converted into a two-phase query for execution
```

## 시간 구간 집계

### TimeBucketCounter

기간을 같은 폭의 구간으로 나눠 구간별 건수를 세는 클래스입니다. 목록 조회에 쓰는 `SearchCondition`을 그대로 받으므로 목록과 집계 결과가 항상 같은 조건을 따릅니다.

```java
public class TimeBucketCounter {
    // Maximum number of buckets a single call may request
    public static final int MAX_BUCKETS = 512;

    public TimeBucketCounter(EntityManager entityManager)

    // Counts matching rows per bucket across the period
    public <T> List<Long> count(Class<T> entityType,
                                JpaSpecificationExecutor<T> repository,
                                SearchCondition<?> condition,
                                String timeAxisField,
                                Instant from,
                                Instant to,
                                int buckets)
}
```

**count() 파라미터**

| 파라미터 | 설명 |
|---------|------|
| `entityType` | 집계 대상 엔티티 클래스 |
| `repository` | 해당 엔티티의 Repository (`JpaSpecificationExecutor` 상속 필요) |
| `condition` | 목록 조회에 사용 중인 검색 조건 |
| `timeAxisField` | 기간을 측정할 엔티티 필드명 (`Instant` 타입) |
| `from` | 기간의 시작 시각 (포함) |
| `to` | 기간의 종료 시각 (제외) |
| `buckets` | 기간을 나눌 구간 수 (1 ~ `MAX_BUCKETS`) |

**반환값**

구간별 건수를 오래된 구간부터 담은 `List<Long>`입니다. 길이는 항상 `buckets`와 같고, 행이 없는 구간은 `0`입니다.

**예외**

| 예외 | 발생 조건 |
|------|----------|
| `IllegalArgumentException` | `from` 또는 `to`가 null이거나, `to`가 `from`보다 뒤가 아닌 경우 |
| `IllegalArgumentException` | `buckets`가 1 미만이거나 `MAX_BUCKETS`(512) 초과인 경우 |

스타터를 사용하면 `timeBucketCounter` 빈이 자동 등록됩니다. 사용 예제는 [고급 기능 - 시간 구간별 건수 집계](advanced-features.md#시간-구간별-건수-집계), 등록 조건과 데이터베이스별 동작은 [자동 설정 가이드 - 시간 구간 집계](auto-configuration.md#시간-구간-집계)를 참조하세요.

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
    public SearchableValidationException(String message)
    public SearchableValidationException(String message, Throwable cause)
}
```

### SearchableParseException

파싱 실패 시 발생하는 예외입니다.

```java
public class SearchableParseException extends SearchableException {
    public SearchableParseException(String message)
    public SearchableParseException(String message, Throwable cause)
}
```

### SearchableConfigurationException

설정 오류 시 발생하는 예외입니다.

```java
public class SearchableConfigurationException extends SearchableException {
    public SearchableConfigurationException(String message)
    public SearchableConfigurationException(String message, Throwable cause)
}
```

### SearchableJoinException

조인 관련 오류 시 발생하는 예외입니다.

```java
public class SearchableJoinException extends SearchableException {
    public SearchableJoinException(String message)
    public SearchableJoinException(String message, Throwable cause)
}
```

### SearchableOperationException

작업 실행 중 오류가 발생할 때 사용하는 예외입니다.

```java
public class SearchableOperationException extends SearchableException {
    public SearchableOperationException(String message)
    public SearchableOperationException(String message, Throwable cause)
}
```

## 유틸리티 클래스

### SearchableFieldUtils

`@SearchableField` 어노테이션을 처리하는 유틸리티입니다.

```java
public class SearchableFieldUtils {
    // Marker returned for composite-key entities (@IdClass or @EmbeddedId)
    public static final String COMPOSITE_KEY_MARKER = "__COMPOSITE_KEY__";

    // Clears all static caches (use in test setup together with @DirtiesContext)
    public static void clearCache()

    // Resolves the entity field name for a DTO field via @SearchableField(entityField)
    public static String getEntityFieldFromDto(Class<?> dtoClass, String field)

    // Finds a declared field by name across the class hierarchy
    public static Field findFieldInHierarchy(Class<?> type, String fieldName)

    // Collects all fields annotated with @SearchableField across the class hierarchy
    public static List<Field> getSearchableFields(Class<?> dtoClass)

    // Resolves the field name to use for sorting (sortField -> entityField -> field name)
    public static String getSortFieldFromDto(Class<?> dtoClass, String field)

    // Resolves the primary key field name via the JPA metamodel
    // (returns COMPOSITE_KEY_MARKER for composite keys)
    public static String getPrimaryKeyFieldName(EntityManager entityManager, Class<?> entityClass)

    // Checks whether the entity uses @EmbeddedId
    public static boolean isEmbeddedIdEntity(EntityManager entityManager, Class<?> entityClass)

    // Resolves the @EmbeddedId attribute name, or null if the entity does not use @EmbeddedId
    public static String getEmbeddedIdAttributeName(EntityManager entityManager, Class<?> entityClass)

    // Resolves all ID field names for composite-key entities (@IdClass or @EmbeddedId)
    public static List<String> getCompositeKeyFieldNames(EntityManager entityManager, Class<?> entityClass)

    // Extracts join paths (e.g. "author", "author.address") required by the given condition nodes
    public static Set<String> extractJoinPaths(List<SearchCondition.Node> nodes)
}
```

### SearchableValueParser

값을 변환하는 유틸리티입니다.

```java
public class SearchableValueParser {
    // Parses a string value to the target type
    public static Object parseValue(String value, Class<?> targetType)

    // Parses a string value to the target type for a between operation.
    // For date/time types with no explicit time component, adjusts the start value to
    // the beginning of day and the end value to the end of day.
    public static Object parseValueForBetween(String value, Class<?> targetType, boolean isEndValue)
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

        @Min(1)
        private int defaultBatchFetchSize = 100;

        @Min(1)
        private int jdbcBatchSize = 1000;

        private boolean batchVersionedData = true;
        private boolean orderInserts = true;
        private boolean orderUpdates = true;
        private boolean inClauseParameterPadding = true;
    }
}
```

`defaultBatchFetchSize`와 `jdbcBatchSize`에는 `@Min(1)` 제약이 적용되어 있어, 1 미만 값으로 설정하면 애플리케이션 구동 시점에 검증 오류가 발생합니다.

## 예제 코드

### 기본 사용 예제

> **상세한 사용 예제**: [기본 사용법](basic-usage.md) 문서에서 완전한 예제 코드를 확인할 수 있습니다.

#### DTO 클래스 정의
```java
// See the basic usage guide for a standard DTO configuration example
// See the advanced features guide for composite-key examples
```

#### 서비스 클래스 구현
```java
// See the basic usage guide for a service implementation example
// See the advanced features guide for advanced scenarios
```

#### 컨트롤러 구현
```java
// See the basic usage guide for a controller implementation example
// See the OpenAPI integration guide for Swagger/OpenAPI setup
```

## 다음 단계

- [FAQ](faq.md) - 자주 묻는 질문
