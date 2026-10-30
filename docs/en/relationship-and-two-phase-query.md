# Relationships and Two-Phase Queries

A filter join decides which roots match; a fetch join initializes associations on the returned roots. Those jobs are different. This page describes `2.0.0`.

## Exposing a related field

```java
import dev.simplecore.searchable.core.annotation.SearchableField;
import static dev.simplecore.searchable.core.condition.operator.SearchOperator.CONTAINS;

public class AuthorSearchDTO {
    @SearchableField(entityField = "author.name", operators = {CONTAINS}, sortable = true,
            sortField = "author.name")
    private String authorName;
    public String getAuthorName() { return authorName; }
}
```

The application entity must actually have an `author` relationship whose target has `name`. Clients send `authorName.contains=Kim` or `sort=authorName.asc`; they do not choose arbitrary entity paths. The DTO annotation is the allowlist.

## Relationship behavior

| Relationship | Effect on root rows | Fetch behavior |
| --- | --- | --- |
| ManyToOne / OneToOne | At most one target per root | Direct root to-one paths are automatically considered for fetching |
| OneToMany / ManyToMany | Can multiply root SQL rows | Filtering/sorting uses joins; paging uses root IDs when a to-many path is involved |
| To-one inside a direct collection | Requires traversing the collection | Automatically detected by default for compatibility; can be disabled |
| Explicit nested fetch | Depends on the path | Applied on entity loading, not count/projection queries |

The automatic scan covers direct to-one attributes and to-one attributes of direct to-many targets. It is not an unlimited recursive graph fetch. Unselected lazy relationships can still issue extra queries when accessed later.

## Explicit Fetch Join (fetchFields)

```java
import java.util.Set;

condition.setFetchFields(Set.of("author", "comments.author", "tags"));
```

These are entity relationship paths configured by server code. `fetchFields` is excluded from JSON binding. Set only paths required by the response; do not let a request select them without an application allowlist. A filter on a child field selects matching roots; the subsequent fetch can load all children of those roots, not only the children that matched the filter.

With a service-managed read transaction, independent first collection paths such as `comments` and `tags` load separately, and the persistence context combines them on the same root instances. Paths sharing a first collection are loaded together. Nested multiple bags or complex mapping constraints can still require application-specific queries.

## Loading and transaction boundaries

`findAllWithSearch` and `findFirstWithSearch` reuse an existing transaction or open a read-only transaction. A read-only transaction does not grant repeatable-read isolation. Associations not initialized inside that boundary may fail on access after detachment. OSIV is an application decision; it is not enabled by this library.

Set the following to avoid automatically fetching collections reached only by the nested detection:

```yaml
searchable:
  hibernate:
    auto-fetch-collections: false
```

Explicit fetches remain enabled. JPA `EAGER`, Hibernate batch fetching, and any relationships accessed by serialization retain their own behavior. There is no universal N+1 elimination guarantee.

## Composite identity

`@IdClass` uses its multiple ID attributes; `@EmbeddedId` resolves the embedded attribute and its component names. ID paging and fetch predicates preserve component equality. Count queries use root matching rather than delimiter-concatenated keys. Follow JPA's identity requirements, including `equals`/`hashCode` for ID classes.

## Practical performance checks

Inspect the SQL for the actual response, including serialization. Count SELECT statements, inspect collection cardinalities, and check indexes on foreign keys and sort/filter columns. Avoid eager bidirectional graphs in JSON responses; map to an application response DTO. For column-only projections or database-specific aggregates, use a dedicated repository method.

[Query Optimization](./two-phase-query-optimization.md) explains direct versus ID-first paging and grouped sort semantics. [Auto Configuration](./auto-configuration.md) lists batching settings.
