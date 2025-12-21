# 설치 가이드

이 문서는 Searchable JPA를 프로젝트에 설치하고 설정하는 방법을 설명합니다.

## 버전 호환성

| 라이브러리 버전 | Spring Boot 버전 | Java 버전 | Jakarta EE | 상태 |
|---------------|----------------|-----------|------------|------|
| `1.0.0+` | `3.2.x+` | `17+` | Jakarta EE 9+ | 최신 버전 |
| `0.1.x` | `2.7.x` | `8+` | javax.* | 지원 중단 예정 |

**중요**: 버전을 혼합해서 사용하지 마세요. 버전에 따라 Jakarta EE와 javax.* 패키지가 다르게 적용됩니다.

## 시스템 요구사항

- **Java 17 이상** (1.0.0+ 버전)
- **Java 8 이상** (0.1.x 버전)
- **Spring Boot 3.2.x+** (1.0.0+ 버전, Jakarta EE 9+)
- **Spring Boot 2.7.x** (0.1.x 버전, javax.* 패키지)
- Spring Data JPA

## 의존성 추가

### Gradle

```gradle
dependencies {
    // Searchable JPA 스타터
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'

    // Spring Boot JPA 스타터 (필수)
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'

    // 데이터베이스 드라이버 (예: H2)
    runtimeOnly 'com.h2database:h2'

    // OpenAPI 통합 (선택사항) - Spring Boot 3.x 버전
    implementation 'org.springdoc:springdoc-openapi-starter-webmvc-ui:2.5.0'
}
```

### Maven

```xml
<dependencies>
    <!-- Searchable JPA 스타터 -->
    <dependency>
        <groupId>dev.simplecore.searchable</groupId>
        <artifactId>spring-boot-starter-searchable-jpa</artifactId>
        <version>${searchable-jpa.version}</version>
    </dependency>

    <!-- Spring Boot JPA 스타터 (필수) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- 데이터베이스 드라이버 (예: H2) -->
    <dependency>
        <groupId>com.h2database</groupId>
        <artifactId>h2</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- OpenAPI 통합 (선택사항) - Spring Boot 3.x 버전 -->
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
  # 데이터소스 설정
  datasource:
    url: jdbc:h2:mem:testdb
    driverClassName: org.h2.Driver
    username: sa
    password: password
  
  # JPA 설정
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        format_sql: true

# Searchable JPA 설정
searchable:
  # Swagger/OpenAPI 통합
  swagger:
    enabled: true

  # Hibernate 최적화 (자동 적용)
  hibernate:
    auto-optimization: true          # 자동 최적화 활성화
    default-batch-fetch-size: 100    # N+1 문제 방지 배치 크기
    jdbc-batch-size: 1000            # JDBC 배치 처리 크기
    batch-versioned-data: true       # 버전 관리 배치 처리
    order-inserts: true              # INSERT 순서 최적화
    order-updates: true              # UPDATE 순서 최적화
    in-clause-parameter-padding: true # IN 절 파라미터 패딩
```

### application.properties

```properties
# 데이터소스 설정
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=password

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# Searchable JPA 설정
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
// 기본 엔티티 설정 예제는 기본 사용법 문서 참조
// 복합 키 엔티티 설정 예제는 고급 기능 문서 참조
```

### 복합 키 엔티티

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
    
    // 복합 키 클래스
    public static class CompositeKey implements Serializable {
        private String tenantId;
        private Long entityId;
        
        // 기본 생성자
        public CompositeKey() {}
        
        public CompositeKey(String tenantId, Long entityId) {
            this.tenantId = tenantId;
            this.entityId = entityId;
        }
        
        // equals, hashCode 구현 (필수)
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
        
        // getter, setter
    }
    
    // getter, setter
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
    
    // 임베디드 키 클래스
    @Embeddable
    public static class CompositeKey implements Serializable {
        @Column(name = "entity_id")
        private Long entityId;
        
        @Column(name = "tenant_id")
        private String tenantId;
        
        // 기본 생성자
        public CompositeKey() {}
        
        public CompositeKey(Long entityId, String tenantId) {
            this.entityId = entityId;
            this.tenantId = tenantId;
        }
        
        // equals, hashCode 구현 (필수)
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
        
        // getter, setter
    }
    
    // getter, setter
}
```

## 레포지토리 설정

### 기본 레포지토리

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
    // JpaSpecificationExecutor 상속 필수
}
```

### 복합 키 레포지토리

```java
// @IdClass 방식
@Repository
public interface MultiTenantEntityRepository 
    extends JpaRepository<MultiTenantEntity, MultiTenantEntity.CompositeKey>, 
            JpaSpecificationExecutor<MultiTenantEntity> {
}

// @EmbeddedId 방식
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
// 서비스 구현 예제는 기본 사용법 문서 참조
// 고급 서비스 기능은 고급 기능 문서 참조
```

### DTO 클래스 정의

> **상세한 DTO 설정**: 완전한 DTO 설정 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// DTO 설정 예제는 기본 사용법 문서 참조
// 복합 키 DTO 설정은 고급 기능 문서 참조
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
    
    // getter, setter
}
```

### 복합 키 검색 DTO

```java
// @IdClass 방식 검색 DTO
public class MultiTenantEntitySearchDTO {
    @SearchableField(operators = {EQUALS, IN})
    private String tenantId;
    
    @SearchableField(operators = {EQUALS, GREATER_THAN, LESS_THAN})
    private Long entityId;
    
    @SearchableField(operators = {CONTAINS, STARTS_WITH})
    private String name;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
    
    // getter, setter
}

// @EmbeddedId 방식 검색 DTO
public class EmbeddedKeyEntitySearchDTO {
    @SearchableField(entityField = "id.entityId", operators = {EQUALS, GREATER_THAN, LESS_THAN})
    private Long entityId;
    
    @SearchableField(entityField = "id.tenantId", operators = {EQUALS, IN})
    private String tenantId;
    
    @SearchableField(operators = {CONTAINS, STARTS_WITH})
    private String name;
    
    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
    
    // getter, setter
}
```

## 컨트롤러 설정

### REST API 컨트롤러

> **상세한 컨트롤러 구현**: 완전한 컨트롤러 구현 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// 컨트롤러 구현 예제는 기본 사용법 문서 참조
// OpenAPI 통합은 OpenAPI 통합 문서 참조
```

## 데이터베이스별 설정

### MySQL

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/searchable_db?useSSL=false&allowPublicKeyRetrieval=true
    username: your_username
    password: your_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  
  jpa:
    database-platform: org.hibernate.dialect.MySQL8Dialect
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
    database-platform: org.hibernate.dialect.PostgreSQL10Dialect
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
    database-platform: org.hibernate.dialect.SQLServer2012Dialect
    hibernate:
      ddl-auto: validate
```

## 성능 최적화 설정

### 인덱스 생성

```sql
-- 기본 검색 인덱스
CREATE INDEX idx_posts_title ON posts(title);
CREATE INDEX idx_posts_status ON posts(status);
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- 복합 인덱스 (검색 + 정렬)
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);

-- 연관 관계 인덱스
CREATE INDEX idx_posts_author_id ON posts(author_id);

-- 복합 키 인덱스
CREATE INDEX idx_multi_tenant_composite ON multi_tenant_entities(tenant_id, entity_id);
CREATE INDEX idx_embedded_key_composite ON embedded_key_entities(tenant_id, entity_id);

-- 부분 검색 인덱스
CREATE INDEX idx_multi_tenant_name ON multi_tenant_entities(tenant_id, name);
```

### Hibernate 최적화

```yaml
searchable:
  hibernate:
    auto-optimization: true          # 자동 최적화 활성화
    default-batch-fetch-size: 100    # N+1 문제 방지 배치 크기
    jdbc-batch-size: 1000            # JDBC 배치 처리 크기
    batch-versioned-data: true       # 버전 관리 배치 처리
    order-inserts: true              # INSERT 순서 최적화
    order-updates: true              # UPDATE 순서 최적화
    in-clause-parameter-padding: true # IN 절 파라미터 패딩 최적화
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

애플리케이션 시작 시 다음 로그가 출력되면 정상적으로 설치된 것입니다:

```
INFO  d.s.s.a.SearchableJpaConfiguration - SearchableJpaConfiguration is being initialized
INFO  d.s.s.a.SearchableJpaConfiguration - Configuring automatic Hibernate optimizations for searchable-jpa...
INFO  d.s.s.a.SearchableJpaConfiguration - Applied Hibernate optimizations:
INFO  d.s.s.a.SearchableJpaConfiguration -   - default_batch_fetch_size: 100
INFO  d.s.s.a.SearchableJpaConfiguration -   - jdbc.batch_size: 1000
INFO  d.s.s.a.SearchableJpaConfiguration -   - order_inserts: true
INFO  d.s.s.a.SearchableJpaConfiguration -   - order_updates: true
INFO  d.s.s.a.SearchableJpaConfiguration -   - in_clause_parameter_padding: true
INFO  d.s.s.a.SearchableJpaConfiguration - These settings help prevent N+1 problems and improve performance automatically.
INFO  d.s.s.a.SearchableJpaConfiguration - To disable auto-optimization, set: searchable.hibernate.auto-optimization=false
```

## 문제 해결

### 일반적인 문제들

1. **"Repository must implement JpaSpecificationExecutor" 오류**
   - 레포지토리에 `JpaSpecificationExecutor<T>` 상속 추가

2. **"Unable to determine ID field name" 오류**
   - 엔티티에 `@Id` 어노테이션 확인
   - 복합 키의 경우 `@IdClass` 또는 `@EmbeddedId` 설정 확인

3. **자동 설정이 작동하지 않는 경우**
   - `spring-boot-starter-searchable-jpa` 의존성 확인
   - Spring Boot 버전 호환성 확인

4. **복합 키 관련 오류**
   - 복합 키 클래스에 `equals()`, `hashCode()` 구현 확인
   - `Serializable` 인터페이스 구현 확인

이제 Searchable JPA가 성공적으로 설치되었습니다! [기본 사용법](basic-usage.md)으로 넘어가서 첫 번째 검색 기능을 구현해보세요.