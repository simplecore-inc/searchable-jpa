# API Reference

This document describes every API and class in Searchable JPA in detail.

## Core Annotations

### @SearchableField

Annotation that defines a searchable field.

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

#### Attributes

| Attribute | Type | Default | Description |
|------|------|--------|------|
| `entityField` | String | `""` | The actual field name on the entity. If empty, the DTO field name is used |
| `operators` | SearchOperator[] | `{}` | Array of search operators to allow. If empty, all operators are allowed |
| `sortable` | boolean | `false` | Whether the field is sortable |
| `sortField` | String | `""` | The field name to use for sorting. If empty, `entityField` or the field name is used |

#### Usage Example

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

Annotation that automatically generates OpenAPI documentation for GET-based search parameters.

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface SearchableParams {
    Class<?> value();
}
```

#### Usage Example

```java
@GetMapping("/search")
public Page<User> search(
    @RequestParam @SearchableParams(UserSearchDTO.class) Map<String, String> params
) {
    // ...
}
```

## Core Classes

### SearchCondition<D>

The core class that defines a search condition.

```java
public class SearchCondition<D> {
    private final List<Node> nodes;
    private Sort sort;
    private Integer page;
    private Integer size;
    private Set<String> fetchFields;
}
```

`@JsonProperty("conditions")` exposes the `nodes` field under the `conditions` key in JSON, but the Java field name and getter remain `nodes` and `getNodes()`. `fetchFields` is a server-only property for explicitly fetch-joining lazy-loaded relationships, and it is ignored during JSON deserialization.

#### Key Methods

| Method | Return Type | Description |
|--------|-----------|------|
| `getNodes()` | `List<Node>` | Returns the list of search condition nodes |
| `getSort()` | `Sort` | Returns the sort condition |
| `getPage()` | `Integer` | Returns the page number |
| `getSize()` | `Integer` | Returns the page size |
| `getFetchFields()` | `Set<String>` | Returns the list of entity fields to fetch-join |
| `setSort(Sort)` | `void` | Sets the sort condition |
| `setPage(Integer)` | `void` | Sets the page number |
| `setSize(Integer)` | `void` | Sets the page size |
| `setFetchFields(Set<String>)` | `void` | Sets the list of entity fields to fetch-join |

#### Static Methods

```java
// Creates a SearchCondition from a JSON string
public static <T> SearchCondition<T> fromJson(String json, Class<T> dtoClass)

// Converts this SearchCondition to a JSON string
public String toJson()
```

### SearchCondition.Condition

Class that represents an individual search condition.

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

#### Constructors

```java
public Condition(String field, SearchOperator searchOperator, Object value, Object value2)
public Condition(LogicalOperator operator, String field, SearchOperator searchOperator, Object value, Object value2, String entityField)
```

The four-argument constructor creates a condition with `operator` and `entityField` left unset, which can be filled in afterward via `setOperator()`/`setEntityField()`. The six-argument constructor is used by Jackson deserialization (`@JsonCreator`) and internal copy logic, where all fields must be specified at once.

### SearchCondition.Group

Class that represents a group of conditions.

```java
public static class Group implements GroupNode {
    private LogicalOperator operator;
    private final List<Node> nodes;
}
```

### SearchCondition.Sort

Class that defines a sort condition.

```java
public static class Sort {
    private final List<Order> orders;
    
    public void addOrder(Order order)
}
```

### SearchCondition.Order

Class that represents an individual sort condition.

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

## Search Operators

### SearchOperator

Enum that defines all search operators.

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

#### Key Methods

```java
public String getName()                                 // Returns the operator name
public static SearchOperator fromName(String operator)   // Finds an operator by name
```

### LogicalOperator

Enum that defines logical operators.

```java
public enum LogicalOperator {
    AND("and"),
    OR("or");
    
    public String getName()
    public static LogicalOperator fromName(String operator)
}
```

## Service Interfaces

### SearchableService<T>

The core service interface that provides search functionality.

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

The `projectionClass` parameter of `findAllWithSearch(SearchCondition<?>, Class<P>)` must be a Spring Data projection **interface**. Passing a concrete class throws `SearchableConfigurationException("Projection class must be an interface")`.

`findOneWithSearch` throws `NonUniqueResultException` if more than one entity matches the condition.

### DefaultSearchableService<T, ID>

The default implementation of `SearchableService`. Use it by extending `DefaultSearchableService` directly.

```java
public class DefaultSearchableService<T, ID> implements SearchableServiceSupport<T, ID> {

    // Constructor
    public DefaultSearchableService(JpaRepository<T, ID> repository, EntityManager entityManager)

    // Returns the delegate that implements all SearchableService methods
    @Override
    public SearchableServiceDelegate<T, ID> getSearchableDelegate()
}
```

Every `SearchableService` method internally delegates to a `SearchableServiceDelegate` instance for execution. A service class only needs to extend `DefaultSearchableService` as before to get the full benefit of two-phase query optimization.

### SearchableServiceSupport<T, ID>

A mixin interface for cases where a class cannot extend `DefaultSearchableService` (for example, because it already extends another class). It provides all `SearchableService` methods as default methods that delegate to a `SearchableServiceDelegate`.

```java
public interface SearchableServiceSupport<T, ID> extends SearchableService<T> {

    // Returns the delegate that provides the actual SearchableService implementation
    SearchableServiceDelegate<T, ID> getSearchableDelegate();
}
```

#### Usage Example

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

A standalone delegate that holds the actual implementation logic for `SearchableService`. You can instantiate it directly and call its methods, or use it indirectly through `SearchableServiceSupport`'s default methods. `DefaultSearchableService` also delegates to this class internally.

```java
public class SearchableServiceDelegate<T, ID> implements SearchableService<T> {

    public SearchableServiceDelegate(JpaRepository<T, ID> repository, EntityManager entityManager, Class<T> entityClass)

    public SpecificationWithPageable<T> createSpecification(SearchCondition<?> searchCondition)
    public SearchableSpecificationBuilder<T> createSpecificationBuilder(SearchCondition<?> searchCondition)
}
```

## Builder Classes

### SearchConditionBuilder<D>

A builder for constructing search conditions programmatically.

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

`from(existing, dtoClass)` deep-copies the nodes, sort, paging, and fetch-join targets of an existing `SearchCondition` to initialize a new builder. Use it when you want to extend a condition further while leaving the original untouched.

### ChainedSearchCondition<D>

An interface for composing search conditions through method chaining.

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

An interface that defines the first condition in a chain.

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

An interface that defines a chained condition.

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

A builder class for constructing sort conditions.

```java
public class SortBuilder {
    public SortBuilder asc(String field);
    public SortBuilder desc(String field);
}
```

## Parser Classes

### SearchableParamsParser<D>

A parser that converts query parameters into a `SearchCondition`.

```java
public class SearchableParamsParser<D> {
    public SearchableParamsParser(Class<D> dtoClass)
    
    public SearchCondition<D> convert(Map<String, String> params)
}
```

#### Supported Parameter Formats

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

## Two-Phase Query Optimization

### Automatic Optimization

Searchable JPA internally uses a two-phase query technique to improve performance. You get the benefit of two-phase query optimization while still using the standard Spring Data `Page<T>` interface as-is.

```java
// Client code stays the same as before
Page<Post> result = postService.findAllWithSearch(condition);

// Internally converted into a two-phase query for execution
```

## Time Bucket Counting

### TimeBucketCounter

Divides a period into buckets of equal width and counts the rows in each. It takes the same `SearchCondition` the list is read with, so the list and the counts always follow the same conditions.

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

**count() Parameters**

| Parameter | Description |
|-----------|-------------|
| `entityType` | The entity class being counted |
| `repository` | That entity's repository (must extend `JpaSpecificationExecutor`) |
| `condition` | The search condition the list is currently filtered by |
| `timeAxisField` | Name of the entity field the period is measured on (of type `Instant`) |
| `from` | Start of the period, inclusive |
| `to` | End of the period, exclusive |
| `buckets` | Number of buckets to divide the period into (1 to `MAX_BUCKETS`) |

**Return Value**

A `List<Long>` holding one count per bucket, oldest first. Its length always equals `buckets`, and a bucket with no matching rows holds `0`.

**Exceptions**

| Exception | Raised When |
|-----------|-------------|
| `IllegalArgumentException` | `from` or `to` is null, or `to` is not after `from` |
| `IllegalArgumentException` | `buckets` is below 1 or above `MAX_BUCKETS` (512) |

With the starter on the classpath, the `timeBucketCounter` bean is registered automatically. See [Advanced Features - Counting Rows per Time Bucket](advanced-features.md#counting-rows-per-time-bucket) for usage examples, and the [Auto-Configuration Guide - Time Bucket Counting](auto-configuration.md#time-bucket-counting) for registration conditions and per-database behavior.

## Exception Classes

### SearchableException

The base exception class for Searchable JPA.

```java
public class SearchableException extends RuntimeException {
    public SearchableException(String message)
    public SearchableException(String message, Throwable cause)
}
```

### SearchableValidationException

Thrown when validation fails.

```java
public class SearchableValidationException extends SearchableException {
    public SearchableValidationException(String message)
    public SearchableValidationException(String message, Throwable cause)
}
```

### SearchableParseException

Thrown when parsing fails.

```java
public class SearchableParseException extends SearchableException {
    public SearchableParseException(String message)
    public SearchableParseException(String message, Throwable cause)
}
```

### SearchableConfigurationException

Thrown when a configuration error occurs.

```java
public class SearchableConfigurationException extends SearchableException {
    public SearchableConfigurationException(String message)
    public SearchableConfigurationException(String message, Throwable cause)
}
```

### SearchableJoinException

Thrown when a join-related error occurs.

```java
public class SearchableJoinException extends SearchableException {
    public SearchableJoinException(String message)
    public SearchableJoinException(String message, Throwable cause)
}
```

### SearchableOperationException

Used when an error occurs during operation execution.

```java
public class SearchableOperationException extends SearchableException {
    public SearchableOperationException(String message)
    public SearchableOperationException(String message, Throwable cause)
}
```

## Utility Classes

### SearchableFieldUtils

A utility for processing the `@SearchableField` annotation.

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

A utility for converting values.

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

## Configuration Classes

### SearchableProperties

The class that defines configuration properties for Searchable JPA.

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

`defaultBatchFetchSize` and `jdbcBatchSize` are constrained by `@Min(1)`, so setting either to a value below 1 causes a validation error at application startup.

## Example Code

### Basic Usage Example

> **Detailed usage examples**: See [Basic Usage](basic-usage.md) for complete example code.

#### DTO Class Definition
```java
// See the basic usage guide for a standard DTO configuration example
// See the advanced features guide for composite-key examples
```

#### Service Class Implementation
```java
// See the basic usage guide for a service implementation example
// See the advanced features guide for advanced scenarios
```

#### Controller Implementation
```java
// See the basic usage guide for a controller implementation example
// See the OpenAPI integration guide for Swagger/OpenAPI setup
```

## Next Steps

- [FAQ](faq.md) - Frequently asked questions
