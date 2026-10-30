# Advanced Features

This page describes service composition, projections, scoped conditions, writes and time counts in `2.0.1-SNAPSHOT`. Examples use the entity and repository from [Basic Usage](./basic-usage.md).

## Service composition

If an existing service must retain its superclass, implement the mixin and return one delegate:

```java
import dev.simplecore.searchable.core.service.SearchableServiceDelegate;
import dev.simplecore.searchable.core.service.SearchableServiceSupport;
import jakarta.persistence.EntityManager;

public class PostService implements SearchableServiceSupport<Post, Long> {
    private final SearchableServiceDelegate<Post, Long> delegate;

    public PostService(PostRepository repository, EntityManager entityManager) {
        delegate = new SearchableServiceDelegate<>(repository, entityManager, Post.class);
    }

    @Override
    public SearchableServiceDelegate<Post, Long> getSearchableDelegate() {
        return delegate;
    }
}
```

Register the service as an application bean when using Spring dependency injection. Use the Spring-managed shared `EntityManager` belonging to the same persistence unit as the repository. Existing `DefaultSearchableService` subclasses continue to use this delegate internally.

## Projections

```java
public interface PostSummary {
    Long getId();
    String getTitle();
}
```

```java
var page = service.findAllWithSearch(condition, PostSummary.class);
```

The projection type must be an interface. A concrete DTO class raises `SearchableConfigurationException`. Projection proxies wrap loaded entities; they do not reduce the SQL SELECT list. Nested or computed getters can access relationships, so arrange fetching and the application's transaction boundary accordingly.

## Extending conditions and mandatory scope

```java
import dev.simplecore.searchable.core.condition.SearchConditionBuilder;

var scoped = SearchConditionBuilder.from(clientCondition, PostSearchDTO.class)
        .and(g -> g.equals("status", "PUBLISHED"))
        .build();
```

The example scopes a client search to published posts. A real tenant/security filter must use a server-derived value and a DTO field allowed for that internal operation. Do not trust a tenant value supplied by the client. Append the mandatory constraint after the client's entire expression; do not append a later OR that can broaden it.

`from` copies node, sort and fetch structures before extension; it does not mutate the original node operators. Mutable arbitrary value objects should still be treated as immutable by callers. Group connectors are evaluated in call order. `A AND (B OR C)` must remain grouped and is not equivalent to `(A AND B) OR C`.

![Group evaluation](./_images/condition-group-evaluation.svg)

## Batch update and delete

```java
public class StatusPatch {
    private String status;
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
```

```java
StatusPatch patch = new StatusPatch();
patch.setStatus("ARCHIVED");
long changed = service.updateWithSearch(condition, patch);
long deleted = service.deleteWithSearch(condition);
```

These are two independent examples: the update can change which rows still match the condition used by a later delete. Both operations process all matching entities, not just the requested page. Empty conditions match the entire entity table; application authorization and mandatory scope must be applied before calling a write method.

Update rules:

- Copy only supplied, non-null persistent properties. A narrow patch DTO does not reset unrelated primitive fields on the entity.
- Ignore identifiers, embedded identifiers, version fields, audit-managed fields, static/final/transient properties and columns marked `updatable=false`.
- Ignore collection properties. Noncollection relationship changes retain their existing behavior and require application authorization.
- Null means “leave unchanged”; this API does not offer an explicit set-to-null marker. Use a dedicated application operation when clearing a value.
- An entity payload's primitive zero/false values are real values. Use nullable fields in a narrow update DTO when omission must be distinguishable from a supplied default.

The executor walks primary keys in chunks of 200, captures the next cursor before mutations and flushes each chunk. Changing a field used in the filter does not shift offsets or skip subsequent keys. Deletes still use repository entity deletion, preserving JPA callbacks and cascades. These methods are not single bulk SQL statements.

An existing transaction is reused. Otherwise the delegate starts a transaction for the whole operation. A chunk is not a separate commit. Roots are detached between chunks only when the delegate owns the transaction; caller-owned persistence contexts retain their attachment behavior. Large cascades or collections can still consume memory. Concurrent writes follow the database's isolation and entity version rules; consider application-level coordination for maintenance jobs.

## Nested paths and composite keys

Expose relationship paths through a DTO alias such as `@SearchableField(entityField="author.name")`. Sort mapping uses `sortField`, then `entityField`, then the DTO name. `@IdClass` and `@EmbeddedId` are resolved through the JPA metamodel; embedded ID attribute names are not assumed to be `id`. Provide valid, comparable ID components for keyset writes and correct entity identity mappings.

Explicit fetch paths are server-controlled: `builder.fetchFields("author", "comments.author")` or `condition.setFetchFields(...)`. They do not grant filter permission. See [Relationships](./relationship-and-two-phase-query.md).

## Counting Rows per Time Bucket

A time bucket is one slice of a fixed period. `TimeBucketCounter` performs aggregation in the database under the supplied search condition. The starter supplies an injectable counter bean; core-only consumers can construct it:

```java
import dev.simplecore.searchable.core.condition.SearchCondition;
import dev.simplecore.searchable.core.service.bucket.TimeBucketCounter;
import java.time.Instant;
import java.util.List;

TimeBucketCounter counter = new TimeBucketCounter(entityManager);
List<Long> counts = counter.count(
        Post.class, repository, new SearchCondition<>(), "createdAt",
        Instant.parse("2026-10-01T00:00:00Z"),
        Instant.parse("2026-11-01T00:00:00Z"), 31);
```

`timeAxisField` names a root entity attribute mapped as `Instant`. It is an entity field name, not a DTO alias or an arbitrary nested path. Validate any application input used to select it. Use `from < to` and 1–512 buckets. The return value always has exactly that many counts in chronological order, including zeros.

The period and every bucket are half-open: `from` is included, `to` excluded, and an internal boundary belongs to the later bucket. Each matching root entity is counted once even if several joined child rows satisfy a filter. Null timestamps are excluded by the time predicate. Pagination, sorting and fetch selection do not select which matching rows are aggregated.

For millisecond-aligned periods evenly divisible by the bucket count, supported dialects can group by a numeric epoch expression. Fractional-millisecond boundaries, uneven divisions, overflow-sensitive arithmetic, Oracle and unknown/unregistered dialects use conditional sums. Boundary calculations use integer nanoseconds; the database column and JDBC mapping still determine stored precision. A sub-millisecond query cannot recover precision that the database discarded.

Numeric expressions are registered for PostgreSQL/CockroachDB, H2, MySQL/MariaDB, SQL Server and SQLite's integer-millisecond storage convention. This list describes code paths, not an integration-test guarantee for every database. Oracle deliberately uses conditional sums because a DATE cast loses fractional seconds. Custom SQLite mappings need their own verification.

Index the time axis and frequent scope predicates. Bucket count affects SQL expression size in the fallback path. Test representative windows and boundary timestamps on the actual database.

## Messages

`MessageUtils` uses the library's `messages/message` bundles and Spring's locale context. Applications can translate errors at the HTTP boundary or configure their own exception response model. A message string is not a stable error code; base programmatic handling on the exception type and application contract.
