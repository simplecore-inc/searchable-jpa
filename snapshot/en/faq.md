# Frequently Asked Questions (FAQ)

## Installation and Setup

### Q: What Spring Boot versions are supported?

**A:** Searchable JPA supports different Spring Boot versions depending on the library version. Key compatibility details:

- **Version 1.0.0+**: Supports only Spring Boot 3.2.x+ (Jakarta EE 9+)
- **Version 0.1.x**: Supports only Spring Boot 2.7.x (uses javax.* packages)
- Java 17+: supported
- JPA 3.0+: supported (version 1.0.0+)
- JPA 2.2+: supported (version 0.1.x)

### Q: Auto-configuration does not work.

**A:** Check the following:

1. **Check the dependency**:
```gradle
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'
    // spring-boot-starter-data-jpa is also required
}
```

2. **Check whether auto-configuration is excluded**: Searchable JPA's auto-configuration activates whenever the starter JAR is on the classpath, regardless of `@ComponentScan` scope. Make sure the following setting has not unintentionally excluded it.
```yaml
spring:
  autoconfigure:
    exclude: []  # Remove this if it contains SearchableJpaConfiguration
```

3. **Check the configuration file**:
```yaml
searchable:
  hibernate:
    auto-optimization: true
```

### Q: Do I need database-specific configuration?

**A:** The database is detected automatically by default, but you can also configure it explicitly:

```yaml
# H2 (for testing)
spring:
  datasource:
    url: jdbc:h2:mem:testdb
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect

# MySQL
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/mydb
  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect

# SQL Server
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=mydb
  jpa:
    database-platform: org.hibernate.dialect.SQLServer2012Dialect
```

## Basic Usage

### Q: How do I implement a SearchableService?

**A:** Extend `DefaultSearchableService` to implement it.

> **Detailed service implementation**: See [Basic Usage](basic-usage.md) for a complete service implementation example.

```java
// See the Basic Usage documentation for a service implementation example
// See the Advanced Features documentation for advanced service capabilities
```

### Q: How do I use the SearchableField annotation?

**A:** Add the `@SearchableField` annotation to fields in your DTO class.

> **Detailed DTO configuration**: See [Basic Usage](basic-usage.md) for a complete DTO configuration example.

```java
// See the Basic Usage documentation for a DTO configuration example
// See the Advanced Features documentation for composite key DTO configuration
```

### Q: How do I handle composite key entities?

**A:** Specify the composite key type when implementing the service:

```java
// @IdClass approach
@Service
public class IdClassEntityService extends DefaultSearchableService<TestIdClassEntity, TestIdClassEntity.CompositeKey> {
    
    public IdClassEntityService(TestIdClassEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}

// @EmbeddedId approach
@Service
public class EmbeddedIdEntityService extends DefaultSearchableService<TestCompositeKeyEntity, TestCompositeKeyEntity.CompositeKey> {
    
    public EmbeddedIdEntityService(TestCompositeKeyEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### Q: How should I define the DTO class?

**A:** Define it using the `@SearchableField` annotation.

> **Detailed DTO configuration**: See [Basic Usage](basic-usage.md) for a complete DTO configuration example.

```java
// See the Basic Usage documentation for a DTO configuration example
// See the Advanced Features documentation for composite key DTO configuration
```

## Search Features

### Q: How do I build complex search conditions?

**A:** There are several approaches:

1. **Query parameter approach**:
```bash
GET /search?title.contains=Spring&status.equals=PUBLISHED&authorName.contains=John
```

2. **JSON approach**:
```json
{
  "conditions": [
    {
      "operator": "and",
      "field": "title",
      "searchOperator": "contains",
      "value": "Spring"
    },
    {
      "operator": "and",
      "field": "status",
      "searchOperator": "equals",
      "value": "PUBLISHED"
    }
  ]
}
```

### Q: How do I search on nested entities?

**A:** Use the `entityField` attribute to specify the nested field:

```java
public class PostSearchDTO {
    // Search by author name
    @SearchableField(entityField = "author.name", operators = {CONTAINS})
    private String authorName;
    
    // Search by author email
    @SearchableField(entityField = "author.email", operators = {EQUALS})
    private String authorEmail;
    
    // Deep nesting (author's department name)
    @SearchableField(entityField = "author.department.name", operators = {EQUALS})
    private String departmentName;
}
```

### Q: How do I search on a date range?

**A:** Use the `BETWEEN` operator:

```bash
# Query parameter approach
GET /search?createdAt.between=2023-01-01,2023-12-31

# Individual condition approach
GET /search?createdAt.greaterThanOrEqualTo=2023-01-01&createdAt.lessThanOrEqualTo=2023-12-31
```

## Performance Optimization

### Q: How do I resolve N+1 query problems?

**A:** Searchable JPA resolves N+1 problems automatically:

```java
// JOIN FETCH is applied automatically
// If a search condition references a related entity field, the join is handled automatically

// If manual configuration is needed
searchable:
  hibernate:
    default-batch-fetch-size: 100
```

### Q: Pagination is slow with large datasets.

**A:** Two-phase query optimization is applied automatically, but you may still need to optimize your indexes:

```sql
-- Create an index on the sort field
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- Create a composite index for composite conditions
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);

-- Composite key index
CREATE INDEX idx_composite_tenant_entity ON test_id_class_entity(tenant_id, entity_id);
```

### Q: When is two-phase query optimization applied?

**A:** It always applies to every search and paginated query, unconditionally. Phase 1 fetches only the matching IDs, applying the search conditions and sort order. Phase 2 batches those IDs into IN clauses and fetches the full entities with fetch joins.

The batch size and default page size are fixed values and cannot be changed through any configuration property:

- IN-clause batch size: 500 records
- Default page size: 20 records

## Composite Keys

### Q: Should I use @IdClass or @EmbeddedId?

**A:** Each has its own trade-offs:

**@IdClass approach**:
- Advantage: keeps the entity class clean
- Drawback: requires defining a separate composite key class

**@EmbeddedId approach**:
- Advantage: guarantees type safety
- Drawback: accessing the entity requires a form like `entity.getId().getTenantId()`

Both approaches use the identical query in the phase-2 lookup: each key field is combined with `AND`, and multiple keys are combined with `OR`. The real difference between the two lies in type safety and code access style, not in query strategy.

```java
// See the Advanced Features documentation for a composite key entity configuration example
```

### Q: Can I search using only part of a composite key?

**A:** Yes, but you need to design your indexes carefully:

```java
// Partial key search DTO
public class PartialKeySearchDTO {
    @SearchableField(operators = {EQUALS})
    private String tenantId;  // Search on only part of the composite key
    
    @SearchableField(operators = {CONTAINS})
    private String name;
}

// Requires index optimization
CREATE INDEX idx_partial_tenant_name ON entity_table(tenant_id, name);
```

## Error Handling

### Q: I get a "Repository must implement JpaSpecificationExecutor" error.

**A:** Add `JpaSpecificationExecutor` to your repository:

```java
// Incorrect
public interface PostRepository extends JpaRepository<Post, Long> {
}

// Correct
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
}
```

### Q: I get a "No composite key fields found for entity" error.

**A:** This error occurs when the composite key class declared with `@IdClass` or `@EmbeddedId` has no discoverable key fields. Double-check your composite key configuration:

```java
// See the Basic Usage documentation for an entity ID configuration example
// See the Advanced Features documentation for a composite key entity configuration example
```

If the entity has no `@Id` at all, you get a failure from the JPA metamodel lookup or the Criteria API instead of this error.

### Q: I get an "Incorrect syntax near ','" error on SQL Server.

**A:** This is a composite key COUNT query issue that is already fixed. Make sure you are using the latest version. To inspect the generated COUNT query directly, turn on the Hibernate SQL log:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

## Advanced Usage

### Q: Can I create custom operators?

**A:** Searchable JPA currently uses an enum-based SearchOperator, so only the built-in operators are available. If you need custom behavior, you can extend the library in the following ways:

1. **Use the built-in operators**: The provided operators cover most search requirements.
2. **Custom service logic**: Implement complex search logic separately in the service layer.
3. **Database functions**: Use the database's built-in functions for the search.

```java
// Recommended: use the built-in operators
@SearchableField(operators = {CONTAINS, STARTS_WITH, ENDS_WITH})
private String title;

// Or handle it separately in the service layer
@Service
public class AdvancedSearchService {

    public Page<Post> searchWithCustomLogic(String query, Pageable pageable) {
        // Implement complex search logic
        if (query.startsWith("fulltext:")) {
            return performFullTextSearch(query.substring(9), pageable);
        }
        // Regular search
        return performRegularSearch(query, pageable);
    }
}
```

### Q: Can I use projections?

**A:** Interface-based projections are supported:

```java
public interface PostSummary {
    String getTitle();
    String getAuthorName();
    LocalDateTime getCreatedAt();
}

// Usage
Page<PostSummary> summaries = postService.findAllWithSearch(condition, PostSummary.class);
```

### Q: How do I perform batch updates or deletes?

**A:** You can run batch operations based on search conditions:

```java
// Batch update
PostUpdateDTO updateData = new PostUpdateDTO();
updateData.setStatus(PostStatus.PUBLISHED);
long updatedCount = postService.updateWithSearch(searchCondition, updateData);

// Batch delete
long deletedCount = postService.deleteWithSearch(searchCondition);
```

## Monitoring and Debugging

### Q: I want to see the queries being executed.

**A:** Adjust the log levels to see them:

```yaml
logging:
  level:
    # Diagnostic logging for Searchable JPA's join paths and fetched fields
    dev.simplecore.searchable: TRACE
    
    # Hibernate SQL logs
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.jdbc.BasicBinder: TRACE
    
    # Spring Data JPA logs
    org.springframework.data.jpa: DEBUG
```

### Q: I want to collect performance metrics.

**A:** Searchable JPA does not emit its own performance logs, so measure execution time directly with AOP:

```java
@Aspect
@Component
public class SearchPerformanceAspect {

    @Around("execution(* dev.simplecore.searchable.core.service.SearchableService.*(..))")
    public Object monitorSearchPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Object result = joinPoint.proceed();
        long duration = System.currentTimeMillis() - startTime;

        log.info("Search method {} executed in {}ms",
            joinPoint.getSignature().getName(), duration);

        return result;
    }
}
```

## Migration

### Q: How do I migrate from existing JPA code?

**A:** You can migrate incrementally:

1. **Step 1**: Add the dependency and enable auto-configuration.
2. **Step 2**: Change your existing service to extend `DefaultSearchableService`.
3. **Step 3**: Define search DTOs and update the controller.
4. **Step 4**: Monitor performance and optimize.

```java
// Can be used alongside your existing code
// See the Basic Usage documentation for a service migration example
```

If you have further questions, open an issue on [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues).
