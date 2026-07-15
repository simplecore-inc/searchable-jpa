# Installation Guide

This document explains how to install and configure Searchable JPA in your project.

## Version Compatibility

| Library Version | Spring Boot Version | Java Version | Jakarta EE | Status |
|---------------|----------------|-----------|------------|------|
| `1.0.0+` | `3.2.x+` | `17+` | Jakarta EE 9+ | Latest |
| `0.1.x` | `2.7.x` | `8+` | javax.* | Deprecated |

**Important**: Do not mix versions. Jakarta EE and javax.* packages apply differently depending on the version you use.

## System Requirements

- **Java 17 or higher** (version 1.0.0+)
- **Java 8 or higher** (version 0.1.x)
- **Spring Boot 3.2.x+** (version 1.0.0+, Jakarta EE 9+)
- **Spring Boot 2.7.x** (version 0.1.x, javax.* packages)
- Spring Data JPA

## Adding the Dependency

### Setting Up GitHub Packages Authentication

Searchable JPA is published only to GitHub Packages. Before you can resolve the dependency, register the repository and configure your credentials.

#### Gradle Repository Configuration

Add the following to `settings.gradle`:

```gradle
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/simplecore-inc/searchable-jpa")
            credentials {
                username = project.findProperty("gpr.user") ?: System.getenv("GITHUB_USERNAME")
                password = project.findProperty("gpr.token") ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

#### Maven Repository Configuration

Register the repository in `pom.xml`:

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/simplecore-inc/searchable-jpa</url>
    </repository>
</repositories>
```

Add your credentials to `~/.m2/settings.xml`:

```xml
<settings>
    <servers>
        <server>
            <id>github</id>
            <username>${env.GITHUB_USERNAME}</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
```

> **Important**: Your GitHub token needs the `read:packages` scope. Generate one at [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens).

### Gradle

```gradle
dependencies {
    // Searchable JPA starter
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'

    // Spring Boot JPA starter (required)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Database driver (e.g., H2)
    runtimeOnly 'com.h2database:h2'

    // OpenAPI integration (optional) - Spring Boot 3.x
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
}
```

### Maven

```xml
<dependencies>
    <!-- Searchable JPA starter -->
    <dependency>
        <groupId>dev.simplecore.searchable</groupId>
        <artifactId>spring-boot-starter-searchable-jpa</artifactId>
        <version>${searchable-jpa.version}</version>
    </dependency>

    <!-- Spring Boot JPA starter (required) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- Database driver (e.g., H2) -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- OpenAPI integration (optional) - Spring Boot 3.x -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.5.0</version>
    </dependency>
</dependencies>
```

## Basic Configuration

### application.yml

```yaml
spring:
  # Datasource configuration
  datasource:
    url: jdbc:h2:mem:testdb
    driverClassName: org.h2.Driver
    username: sa
    password: password
  
  # JPA configuration
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# Searchable JPA configuration
searchable:
  # Swagger/OpenAPI integration
  swagger:
    enabled: true

  # Hibernate optimizations (applied automatically)
  hibernate:
    auto-optimization: true          # Enable automatic optimization
    default-batch-fetch-size: 100    # Batch size to prevent N+1 queries
    jdbc-batch-size: 1000            # JDBC batch processing size
    batch-versioned-data: true       # Batch processing for versioned entities
    order-inserts: true              # Optimize INSERT ordering
    order-updates: true              # Optimize UPDATE ordering
    in-clause-parameter-padding: true # IN clause parameter padding
```

### application.properties

```properties
# Datasource configuration
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password

# JPA configuration
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Searchable JPA configuration
searchable.swagger.enabled=true
searchable.hibernate.auto-optimization=true
searchable.hibernate.default-batch-fetch-size=100
searchable.hibernate.jdbc-batch-size=1000
searchable.hibernate.batch-versioned-data=true
searchable.hibernate.order-inserts=true
searchable.hibernate.order-updates=true
searchable.hibernate.in-clause-parameter-padding=true
```

## Entity Configuration

### Basic Entity Configuration

> **Detailed entity configuration**: See [Basic Usage](basic-usage.md) for a complete entity configuration example.

```java
// See the basic usage guide for a complete entity configuration example
// See the advanced features guide for composite key entity configuration examples
```

### Composite Key Entity Configuration

#### The @IdClass Approach

```java
@Entity
@Table(name = "multi_tenant_entities")
@IdClass(MultiTenantEntity.CompositeKey.class)
public class MultiTenantEntity {
    @Id
    @Column(name = "tenant_id")
    private String tenantId;
    
    @Id
    @Column(name = "entity_id")
    private Long entityId;
    
    private String name;
    private String description;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Composite key class
    public static class CompositeKey implements Serializable {
        private String tenantId;
        private Long entityId;
        
        // Default constructor
        public CompositeKey() {}
        
        public CompositeKey(String tenantId, Long entityId) {
            this.tenantId = tenantId;
            this.entityId = entityId;
        }
        
        // equals, hashCode implementation (required)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKey that = (CompositeKey) o;
            return Objects.equals(tenantId, that.tenantId) &&
                   Objects.equals(entityId, that.entityId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(tenantId, entityId);
        }
        
        // getters and setters
    }
    
    // getters and setters
}
```

#### The @EmbeddedId Approach

```java
@Entity
@Table(name = "embedded_key_entities")
public class EmbeddedKeyEntity {
    @EmbeddedId
    private CompositeKey id;
    
    private String name;
    private String description;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    // Embedded key class
    @Embeddable
    public static class CompositeKey implements Serializable {
        @Column(name = "entity_id")
        private Long entityId;
        
        @Column(name = "tenant_id")
        private String tenantId;
        
        // Default constructor
        public CompositeKey() {}
        
        public CompositeKey(Long entityId, String tenantId) {
            this.entityId = entityId;
            this.tenantId = tenantId;
        }
        
        // equals, hashCode implementation (required)
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CompositeKey that = (CompositeKey) o;
            return Objects.equals(entityId, that.entityId) &&
                   Objects.equals(tenantId, that.tenantId);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(entityId, tenantId);
        }
        
        // getters and setters
    }
    
    // getters and setters
}
```

## Repository Configuration

### Basic Repository

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    // Must extend JpaSpecificationExecutor
}
```

### Composite Key Repository

```java
// @IdClass approach
@Repository
public interface MultiTenantEntityRepository 
    extends JpaRepository<MultiTenantEntity, MultiTenantEntity.CompositeKey>, 
            JpaSpecificationExecutor<MultiTenantEntity> {
}

// @EmbeddedId approach
@Repository
public interface EmbeddedKeyEntityRepository 
    extends JpaRepository<EmbeddedKeyEntity, EmbeddedKeyEntity.CompositeKey>, 
            JpaSpecificationExecutor<EmbeddedKeyEntity> {
}
```

## Service Configuration

### Implementing SearchableService

> **Detailed service implementation**: See [Basic Usage](basic-usage.md) for a complete service implementation example.

```java
// See the basic usage guide for a service implementation example
// See the advanced features guide for advanced service capabilities
```

### Defining the DTO Class

> **Detailed DTO configuration**: See [Basic Usage](basic-usage.md) for a complete DTO configuration example.

```java
// See the basic usage guide for a DTO configuration example
// See the advanced features guide for composite key DTO configuration
```

## Defining the Search DTO

### Basic Search DTO

```java
public class PostSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String title;
    
    @SearchableField(operators = {EQUALS, IN})
    private PostStatus status;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
    private Integer viewCount;
    
    @SearchableField(entityField = "author.name", operators = {CONTAINS})
    private String authorName;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN, BETWEEN}, sortable = true)
    private LocalDateTime createdAt;
    
    // getters and setters
}
```

### Composite Key Search DTO

```java
// Search DTO for the @IdClass approach
public class MultiTenantEntitySearchDTO {
    @SearchableField(operators = {EQUALS, IN})
    private String tenantId;
    
    @SearchableField(operators = {EQUALS, GREATER_THAN, LESS_THAN})
    private Long entityId;
    
    @SearchableField(operators = {CONTAINS, STARTS_WITH})
    private String name;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
    
    // getters and setters
}

// Search DTO for the @EmbeddedId approach
public class EmbeddedKeyEntitySearchDTO {
    @SearchableField(entityField = "id.entityId", operators = {EQUALS, GREATER_THAN, LESS_THAN})
    private Long entityId;
    
    @SearchableField(entityField = "id.tenantId", operators = {EQUALS, IN})
    private String tenantId;
    
    @SearchableField(operators = {CONTAINS, STARTS_WITH})
    private String name;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
    
    // getters and setters
}
```

## Controller Configuration

### REST API Controller

> **Detailed controller implementation**: See [Basic Usage](basic-usage.md) for a complete controller implementation example.

```java
// See the basic usage guide for a controller implementation example
// See the OpenAPI integration guide for OpenAPI integration details
```

## Database-Specific Configuration

Hibernate 6 automatically detects the database dialect from the JDBC URL and driver, so you do not need to set `database-platform` separately, as shown in the examples below.

### MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/searchable_db?useSSL=false&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
```

### PostgreSQL

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/searchable_db
    username: your_username
    password: your_password
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
```

### SQL Server

```yaml
spring:
  datasource:
    url: jdbc:sqlserver://localhost:1433;databaseName=searchable_db;trustServerCertificate=true
    username: your_username
    password: your_password
    driver-class-name: com.microsoft.sqlserver.jdbc.SQLServerDriver
  
  jpa:
    hibernate:
      ddl-auto: validate
```

## Performance Optimization Settings

### Creating Indexes

```sql
-- Basic search indexes
CREATE INDEX idx_posts_title ON posts(title);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- Composite index (search + sort)
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);

-- Association index
CREATE INDEX idx_posts_author_id ON posts(author_id);

-- Composite key indexes
CREATE INDEX idx_multi_tenant_composite ON multi_tenant_entities(tenant_id, entity_id);
CREATE INDEX idx_embedded_key_composite ON embedded_key_entities(tenant_id, entity_id);

-- Partial search index
CREATE INDEX idx_multi_tenant_name ON multi_tenant_entities(tenant_id, name);
```

### Hibernate Optimization

```yaml
searchable:
  hibernate:
    auto-optimization: true          # Enable automatic optimization
    default-batch-fetch-size: 100    # Batch size to prevent N+1 queries
    jdbc-batch-size: 1000            # JDBC batch processing size
    batch-versioned-data: true       # Batch processing for versioned entities
    order-inserts: true              # Optimize INSERT ordering
    order-updates: true              # Optimize UPDATE ordering
    in-clause-parameter-padding: true # IN clause parameter padding optimization
```

## Verifying the Installation

### Test Controller

```java
@RestController
@RequestMapping("/api/test")
public class TestController {
    
    private final PostService postService;
    
    public TestController(PostService postService) {
        this.postService = postService;
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> healthCheck() {
        return ResponseEntity.ok("Searchable JPA is working!");
    }
    
    @GetMapping("/search-test")
    public Page<Post> searchTest() {
        SearchCondition<PostSearchDTO> condition = SearchConditionBuilder
            .create(PostSearchDTO.class)
            .where(w -> w
                .equals("status", "PUBLISHED"))
            .page(0)
            .size(10)
            .build();
        return postService.findAllWithSearch(condition);
    }
}
```

### Confirming Application Startup

Searchable JPA logs its initialization at the TRACE level. Spring Boot's default log level (INFO) does not show TRACE output, so add the following configuration if you want to confirm the installation through logs.

```yaml
logging:
  level:
    dev.simplecore.searchable: TRACE
```

After adding this configuration, starting the application produces the following log output:

```
TRACE d.s.s.a.SearchableJpaConfiguration - SearchableJpaConfiguration is being initialized
TRACE d.s.s.a.SearchableJpaConfiguration - Configuring automatic Hibernate optimizations for searchable-jpa...
TRACE d.s.s.a.SearchableJpaConfiguration - Applied Hibernate optimizations:
TRACE d.s.s.a.SearchableJpaConfiguration -   - default_batch_fetch_size: 100
TRACE d.s.s.a.SearchableJpaConfiguration -   - jdbc.batch_size: 1000
TRACE d.s.s.a.SearchableJpaConfiguration -   - order_inserts: true
TRACE d.s.s.a.SearchableJpaConfiguration -   - order_updates: true
TRACE d.s.s.a.SearchableJpaConfiguration -   - in_clause_parameter_padding: true
TRACE d.s.s.a.SearchableJpaConfiguration - These settings help prevent N+1 problems and improve performance automatically.
TRACE d.s.s.a.SearchableJpaConfiguration - To disable auto-optimization, set: searchable.hibernate.auto-optimization=false
```

Even without this log output, automatic optimization is already applied by default. The log is only a way to confirm that it ran.

## Troubleshooting

### Common Issues

1. **"Repository must implement JpaSpecificationExecutor" error**
   - Add `JpaSpecificationExecutor<T>` to the repository's extends clause

2. **"Could not determine primary key field for entity ..." warning**
   - Check that the entity has an `@Id` annotation
   - For composite keys, check the `@IdClass` or `@EmbeddedId` configuration
   - This is a WARN-level log; it means pagination order may be inconsistent when the sorted results contain duplicate values

3. **Auto-configuration does not work**
   - Check the `spring-boot-starter-searchable-jpa` dependency
   - Check Spring Boot version compatibility

4. **Composite key errors**
   - Check that the composite key class implements `equals()` and `hashCode()`
   - Check that it implements the `Serializable` interface

Searchable JPA is now installed. Continue to [Basic Usage](basic-usage.md) to implement your first search feature.
