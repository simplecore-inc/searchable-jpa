# API Reference

Public application APIs for `1.1.4-SNAPSHOT`. Import types from the packages shown below; Java examples use simple type names after imports. Internal query executors are implementation details rather than an application extension contract.

## Annotations

`SearchableField` is in `dev.simplecore.searchable.core.annotation`, targets fields and is retained at runtime.

| Attribute | Type | Default | Meaning |
| --- | --- | --- | --- |
| `entityField` | `String` | `""` | Entity search path; empty uses DTO field name |
| `operators` | `SearchOperator[]` | `{}` | Empty permits all operators, with type/value validation |
| `sortable` | `boolean` | false | Whether the DTO field is allowed in sort input |
| `sortField` | `String` | `""` | Sort path; falls back to entityField, then DTO field name |

`SearchableParams` is in `dev.simplecore.searchable.openapi.annotation`. Its required `Class<?> value()` identifies the DTO for OpenAPI documentation on a controller parameter. It does not perform request conversion.

## SearchCondition<D>

Package: `dev.simplecore.searchable.core.condition`.

| API | Behavior |
| --- | --- |
| `new SearchCondition<>()` | Empty node list and fetch set; pagination may remain unset |
| `new SearchCondition<>(nodes, page, size)` | Initializes condition data; callers must not assume deep copying |
| `getNodes()` | The ordered node list; JSON property is `conditions` |
| `getPage()` / `setPage(Integer)` | Nullable zero-based page |
| `getSize()` / `setSize(Integer)` | Nullable requested size |
| `getSort()` / `setSort(Sort)` | Sort definition |
| `getFetchFields()` / `setFetchFields(Set<String>)` | Server-only entity fetch paths |
| `fromJson(String, Class<D>)` | Typed Jackson 2 conversion and DTO validation |
| `toJson()` | Pretty JSON using the library mapper |

JSON helper methods retain the Jackson 2 `JsonProcessingException` signature. Validation errors have dedicated library exception types. Typed Jackson 3 body binding uses an adapter to the same validation pipeline. Both generations accept the documented lowercase/camelCase wire operator names. `fetchFields` is ignored on JSON input/output; submitted `entityField` values are resolved from DTO mapping rather than trusted.

Nested types:

- `Node`: logical connector access (`getOperator`/`setOperator`).
- `Condition`: constructor `(LogicalOperator, String field, SearchOperator, Object value, Object value2, String entityField)`; getters for condition data; mutable connector and resolved entity path.
- `Group`: empty constructor or `(LogicalOperator, List<Node>)`; ordered children and outer connector.
- `Sort`: ordered `Order` list, `addOrder(Order)`.
- `Order`: `(String field, Direction)` or `(String field, Direction, String entityField)`; `isAscending()` remains serialized for compatibility but is ignored as input.
- `Direction`: `ASC` / `DESC`, wire values `asc` / `desc`.

## Builders

| API | Purpose |
| --- | --- |
| `SearchConditionBuilder.create(DTO.class)` | Begin DTO-aware construction |
| `SearchConditionBuilder.from(existing, DTO.class)` | Copy structures before extending |
| `where(Consumer<ConditionGroupBuilder>)` | Append initial conditions in declared order |
| `and(...)` / `or(...)` | Combine a group with the accumulated expression |
| `sort(Consumer<SortBuilder>)` | Specify sorting; `asc(field)` and `desc(field)` |
| `page(int)` / `size(int)` | Set pagination |
| `fetchFields(String...)` / `fetchFields(Set<String>)` | Add server fetch paths |
| `build()` | Check structural and DTO constraints, then return the condition |

Sort, page, size and fetch methods return `ChainedSearchCondition<D>`, preserving the fluent API. `FirstCondition` and `ChainedCondition` expose the 18 operators in [Search Operators](./search-operators.md). The latter also exposes corresponding `or...` methods and nested `and`/`or` consumers. Range methods take two bounds; IN methods take a collection. Null checks need only a field name.

## URL parser

`new SearchableParamsParser<D>(DTO.class).convert(Map<String,String>)` accepts `field.operator=value`, `sort=field.asc,other.desc`, `page` and `size`. Filter entries are ANDed. Only IN/NOT_IN and BETWEEN/NOT_BETWEEN split commas; ordinary string equality/contains preserves commas. Repeated query keys cannot be represented independently in a single-value Map. Use JSON for groups or literal commas inside an IN member.

## Service API

`SearchableService<T>` is in `dev.simplecore.searchable.core.service`.

| Method | Return | Semantics |
| --- | --- | --- |
| `findAllWithSearch(condition)` | `Page<T>` | Matching roots, pagination, sort and fetch |
| `findAllWithSearch(condition, projectionInterface)` | `Page<P>` | Entity-backed interface projections |
| `findOneWithSearch(condition)` | `Optional<T>` | No row: empty; more than one: nonunique-result error; not a first-row shortcut |
| `findFirstWithSearch(condition)` | `Optional<T>` | First sorted root, ignores supplied page offset, no total count |
| `countWithSearch(condition)` | `long` | Number of matching roots |
| `existsWithSearch(condition)` | `boolean` | Whether a matching root exists |
| `updateWithSearch(condition, updateData)` | `long` | All matching roots, protected-field rules and 200-entity chunks |
| `deleteWithSearch(condition)` | `long` | All matching roots, entity callbacks/cascades and 200-entity chunks |

`DefaultSearchableService<T,ID>` takes `(JpaRepository<T,ID>, EntityManager)` and resolves the entity type from the concrete subclass. `SearchableServiceDelegate<T,ID>` takes those arguments plus `Class<T>`. The repository must also implement `JpaSpecificationExecutor<T>`. `SearchableServiceSupport<T,ID>` supplies default service methods through `getSearchableDelegate()`.

A count/existence/unique/write operation does not restrict its result to the requested page, although common condition validation still applies. `findOne` may be translated by Spring Data to an `IncorrectResultSizeDataAccessException`; do not depend on one provider-specific wrapper type. [Advanced Features](./advanced-features.md) defines update and transaction semantics.

## TimeBucketCounter

Package: `dev.simplecore.searchable.core.service.bucket`.

```java
import dev.simplecore.searchable.core.condition.SearchCondition;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

// Signature on TimeBucketCounter:
// <T> List<Long> count(Class<T> entityType, JpaSpecificationExecutor<T> repository,
//     SearchCondition<?> condition, String timeAxisField,
//     Instant from, Instant to, int buckets)
```

Construct with an `EntityManager` or inject the starter bean. `MAX_BUCKETS` is 512. The root time attribute must be `Instant`; the period is `[from,to)`. Invalid period/bucket count raises `IllegalArgumentException`. Counts have fixed length and count root entities once. See [time-count rules](./advanced-features.md#counting-rows-per-time-bucket).

## Exceptions and validation

`SearchableException` extends `RuntimeException`. Its subclasses are `SearchableValidationException`, `SearchableParseException`, `SearchableConfigurationException`, `SearchableOperationException` and `SearchableJoinException`. They do not extend Jakarta Validation's `ValidationException`.

`SearchableFieldValidator` checks annotated fields and sorting permissions. `SearchConditionValidator` checks operators, converts values and applies Bean Validation to the searched property values, including both range bounds. It does not validate an otherwise empty DTO or require unrelated `@NotNull` values. Null-check operators do not validate a supplied value. `SearchRequestLimits` applies structural and executed page limits described in [Auto Configuration](./auto-configuration.md).

## Utilities

| Type | Main functions |
| --- | --- |
| `SearchableFieldUtils` | DTO field hierarchy, mapping/sort paths, allowed operators, primary/composite key metadata, join paths, `clearCache()` |
| `SearchableValueParser` | `parseValue(String, Class<?>)`, `parseValueForBetween(String, Class<?>, boolean isEndValue)` |
| `SearchableTimeZoneHolder` | Shared application zone used by temporal conversion |
| `MessageUtils` | Localized library message lookup |

Prefer builder/parser/service APIs for application requests. Direct node construction and metadata helpers do not replace DTO allowlist checks.
