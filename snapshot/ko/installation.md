# 설치 가이드

이 문서는 Searchable JPA를 프로젝트에 설치하고 설정하는 방법을 설명합니다.

## 버전 호환성

| 라이브러리 버전 | Spring Boot 버전 | Java 버전 | Jakarta EE | 상태 |
|---------------|----------------|-----------|------------|------|
| `2.x` | `4.1.x+` | `17+` | Jakarta EE 11 | 최신 버전 |
| `1.x` | `3.2.x` ~ `3.5.x` | `17+` | Jakarta EE 9+ | 유지보수 |
| `0.1.x` | `2.7.x` | `8+` | javax.* | 유지보수 종료 |

**중요**: 버전을 혼합해서 사용하지 마세요. 버전에 따라 Jakarta EE와 javax.* 패키지가 다르게 적용됩니다.

## 시스템 요구사항

- **Java 17 이상** (2.x · 1.x 버전)
- **Java 8 이상** (0.1.x 버전)
- **Spring Boot 4.1.x+** (2.x 버전, Jakarta EE 11)
- **Spring Boot 3.2.x ~ 3.5.x** (1.x 버전, Jakarta EE 9+)
- **Spring Boot 2.7.x** (0.1.x 버전, javax.* 패키지)
- Spring Data JPA

## 의존성 추가

### GitHub Packages 인증 설정

Searchable JPA는 GitHub Packages에만 배포됩니다. 의존성을 내려받으려면 저장소 등록과 인증 정보 설정을 먼저 마쳐야 합니다.

#### Gradle 저장소 설정

`settings.gradle`에 다음을 추가하세요.

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

#### Maven 저장소 설정

`pom.xml`에 저장소를 등록하세요.

```xml
<repositories>
    <repository>
        <id>github</id>
        <url>https://maven.pkg.github.com/simplecore-inc/searchable-jpa</url>
    </repository>
</repositories>
```

`~/.m2/settings.xml`에 인증 정보를 추가하세요.

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

> **중요**: GitHub 토큰에 `read:packages` 권한이 필요합니다. [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)에서 생성하세요.

### Gradle

```gradle
dependencies {
    // Searchable JPA starter
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'

    // Spring Boot JPA starter (required)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // Database driver (e.g., H2)
    runtimeOnly 'com.h2database:h2'

    // OpenAPI integration (optional) - SpringDoc 3.x for Spring Boot 4 (searchable-jpa 2.x),
    // SpringDoc 2.x for Spring Boot 3 (searchable-jpa 1.x)
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.0'
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

    <!-- OpenAPI integration (optional) - SpringDoc 3.x for Spring Boot 4, 2.x for Spring Boot 3 -->
    <dependency>
        <groupId>org.springdoc</groupId>
        <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
        <version>2.5.0</version>
    </dependency>
</dependencies>
```

## 기본 설정

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

## 엔티티 설정

### 기본 엔티티 설정

> **상세한 엔티티 설정**: 완전한 엔티티 설정 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// See the basic usage guide for a complete entity configuration example
// See the advanced features guide for composite key entity configuration examples
```

### 복합 키 엔티티 설정

#### @IdClass 방식

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

#### @EmbeddedId 방식

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

## 저장소 설정

### 기본 저장소

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    // Must extend JpaSpecificationExecutor
}
```

### 복합 키 저장소

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

## 서비스 설정

### SearchableService 구현

> **상세한 서비스 구현**: 완전한 서비스 구현 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// See the basic usage guide for a service implementation example
// See the advanced features guide for advanced service capabilities
```

### DTO 클래스 정의

> **상세한 DTO 설정**: 완전한 DTO 설정 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// See the basic usage guide for a DTO configuration example
// See the advanced features guide for composite key DTO configuration
```

## 검색 DTO 정의

### 기본 검색 DTO

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

### 복합 키 검색 DTO

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

## 컨트롤러 설정

### REST API 컨트롤러

> **상세한 컨트롤러 구현**: 완전한 컨트롤러 구현 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// See the basic usage guide for a controller implementation example
// See the OpenAPI integration guide for OpenAPI integration details
```

## 데이터베이스별 설정

Hibernate 6은 JDBC URL과 드라이버로 데이터베이스 dialect를 자동으로 인식하므로, 아래 예제처럼 `database-platform`을 별도로 지정할 필요가 없습니다.

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

## 성능 최적화 설정

### 인덱스 생성

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

### Hibernate 최적화

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

## 설치 검증

### 테스트 컨트롤러

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

### 애플리케이션 시작 확인

Searchable JPA의 초기화 로그는 TRACE 레벨로 남습니다. Spring Boot 기본 로그 레벨(INFO)에서는 표시되지 않으므로, 로그로 설치를 확인하려면 다음 설정을 추가하세요.

```yaml
logging:
  level:
    dev.simplecore.searchable: TRACE
```

설정 후 애플리케이션을 시작하면 다음 로그가 출력됩니다.

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

이 로그가 보이지 않아도 자동 최적화는 기본값으로 적용됩니다. 이 로그는 최적화 진행 상태를 확인할 때 사용합니다.

## 문제 해결

### 일반적인 문제들

1. **"Repository must implement JpaSpecificationExecutor" 오류**
   - 저장소에 `JpaSpecificationExecutor<T>` 상속 추가

2. **"Could not determine primary key field for entity ..." 경고**
   - 엔티티에 `@Id` 어노테이션이 있는지 확인
   - 복합 키는 `@IdClass` 또는 `@EmbeddedId` 설정 확인
   - WARN 레벨 로그이며, 정렬 결과에 동일한 값이 있으면 페이지네이션 순서가 정확하지 않을 수 있음을 알려줌

3. **자동 설정이 작동하지 않는 경우**
   - `spring-boot-starter-searchable-jpa` 의존성 확인
   - Spring Boot 버전 호환성 확인

4. **복합 키 관련 오류**
   - 복합 키 클래스에 `equals()`, `hashCode()` 구현 확인
   - `Serializable` 인터페이스 구현 확인

Searchable JPA 설치를 마쳤으면 [기본 사용법](basic-usage.md)에서 첫 번째 검색 기능을 구현해 보세요.
