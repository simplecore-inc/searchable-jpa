# 자주 묻는 질문 (FAQ)

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: API 레퍼런스](api-reference.md)

---

## 설치 및 설정

### Q: Spring Boot 버전 호환성은 어떻게 되나요?

**A:** Searchable JPA는 버전별로 다른 Spring Boot 버전을 지원합니다. 주요 호환성 정보:

- **1.0.0+ 버전**: Spring Boot 3.2.x+만 지원 (Jakarta EE 9+)
- **0.1.x 버전**: Spring Boot 2.7.x만 지원 (javax.* 패키지 사용)
- Java 17+: 지원
- JPA 3.0+: 지원 (1.0.0+ 버전)
- JPA 2.2+: 지원 (0.1.x 버전)

### Q: 자동 설정이 작동하지 않아요.

**A:** 다음 사항을 확인해주세요:

1. **의존성 확인**:
```gradle
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:1.0.0-SNAPSHOT'
    // spring-boot-starter-data-jpa도 필요합니다
}
```

2. **패키지 스캔 확인**:
```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

3. **설정 파일 확인**:
```yaml
searchable:
  hibernate:
    auto-optimization: true
```

### Q: 데이터베이스별 설정이 필요한가요?

**A:** 기본적으로 자동 감지되지만, 명시적 설정도 가능합니다:

```yaml
# H2 (테스트용)
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

## 기본 사용법

### Q: SearchableService를 어떻게 구현하나요?

A: `DefaultSearchableService`를 상속받아 구현합니다.

> **상세한 서비스 구현**: 완전한 서비스 구현 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// 서비스 구현 예제는 기본 사용법 문서 참조
// 고급 서비스 기능은 고급 기능 문서 참조
```

### Q: SearchableField 어노테이션을 어떻게 사용하나요?

A: DTO 클래스의 필드에 `@SearchableField` 어노테이션을 추가합니다.

> **상세한 DTO 설정**: 완전한 DTO 설정 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// DTO 설정 예제는 기본 사용법 문서 참조
// 복합 키 DTO 설정은 고급 기능 문서 참조
```

### Q: 복합 키 엔티티는 어떻게 처리하나요?

**A:** 복합 키 타입을 명시하여 구현합니다:

```java
// @IdClass 방식
@Service
public class IdClassEntityService extends DefaultSearchableService<TestIdClassEntity, TestIdClassEntity.CompositeKey> {
    
    public IdClassEntityService(TestIdClassEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}

// @EmbeddedId 방식
@Service
public class EmbeddedIdEntityService extends DefaultSearchableService<TestCompositeKeyEntity, TestCompositeKeyEntity.CompositeKey> {
    
    public EmbeddedIdEntityService(TestCompositeKeyEntityRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### Q: DTO 클래스는 어떻게 정의해야 하나요?

A: `@SearchableField` 어노테이션을 사용하여 정의합니다.

> **상세한 DTO 설정**: 완전한 DTO 설정 예제는 [기본 사용법](basic-usage.md) 문서를 참조하세요.

```java
// DTO 설정 예제는 기본 사용법 문서 참조
// 복합 키 DTO 설정은 고급 기능 문서 참조
```

## 검색 기능

### Q: 복잡한 검색 조건은 어떻게 구성하나요?

**A:** 여러 방법이 있습니다:

1. **쿼리 파라미터 방식**:
```bash
GET /search?title.contains=Spring&status.equals=PUBLISHED&authorName.contains=John
```

2. **JSON 방식**:
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

### Q: 중첩된 엔티티 검색은 어떻게 하나요?

**A:** `entityField` 속성을 사용하여 중첩 필드를 지정합니다:

```java
public class PostSearchDTO {
    // 작성자 이름으로 검색
    @SearchableField(entityField = "author.name", operators = {CONTAINS})
    private String authorName;
    
    // 작성자 이메일로 검색
    @SearchableField(entityField = "author.email", operators = {EQUALS})
    private String authorEmail;
    
    // 깊은 중첩 (작성자의 부서명)
    @SearchableField(entityField = "author.department.name", operators = {EQUALS})
    private String departmentName;
}
```

### Q: 날짜 범위 검색은 어떻게 하나요?

**A:** `BETWEEN` 연산자를 사용합니다:

```bash
# 쿼리 파라미터 방식
GET /search?createdAt.between=2023-01-01,2023-12-31

# 개별 조건 방식
GET /search?createdAt.greaterThanOrEqualTo=2023-01-01&createdAt.lessThanOrEqualTo=2023-12-31
```

## 성능 최적화

### Q: N+1 문제가 발생하는데 어떻게 해결하나요?

**A:** Searchable JPA는 자동으로 N+1 문제를 해결합니다:

```java
// 자동으로 JOIN FETCH가 적용됩니다
// 검색 조건에 연관 엔티티 필드가 있으면 자동 JOIN 처리

// 수동 설정이 필요한 경우
searchable:
  hibernate:
    default-batch-fetch-size: 100
```

### Q: 대용량 데이터에서 페이징 성능이 느려요.

**A:** 2단계 쿼리 최적화가 자동으로 적용되지만, 인덱스 최적화가 필요할 수 있습니다:

```sql
-- 정렬 필드에 인덱스 생성
CREATE INDEX idx_posts_created_at ON posts(created_at DESC);

-- 복합 조건에 복합 인덱스 생성
CREATE INDEX idx_posts_status_created_at ON posts(status, created_at DESC);

-- 복합 키 인덱스
CREATE INDEX idx_composite_tenant_entity ON test_idclass_entity(tenant_id, entity_id);
```

### Q: 2단계 쿼리 최적화는 언제 적용되나요?

**A:** 다음 조건에서 자동으로 적용됩니다:

- 복잡한 JOIN이 포함된 검색
- 복합 키 엔티티 검색
- ToMany 관계가 포함된 검색
- 대용량 데이터 검색

```java
// 로그로 확인 가능
logging:
  level:
    dev.simplecore.searchable.core.service.specification.TwoPhaseQueryExecutor: DEBUG
```

## 복합 키 관련

### Q: @IdClass와 @EmbeddedId 중 어느 것을 사용해야 하나요?

**A:** 각각의 장단점이 있습니다:

**@IdClass 방식**:
- 장점: 엔티티 클래스가 깔끔함
- 단점: 복합 키 클래스 별도 정의 필요
- 쿼리: 복합 OR 조건 사용

**@EmbeddedId 방식**:
- 장점: 타입 안전성, 더 최적화된 쿼리
- 단점: 엔티티 접근 시 `entity.getId().getTenantId()` 형태
- 쿼리: IN 조건으로 최적화

```java
// 성능상 @EmbeddedId가 약간 유리 (IN 조건 최적화)
// 복합 키 엔티티 설정 예제는 고급 기능 문서 참조
```

### Q: 복합 키 검색에서 부분 키로만 검색할 수 있나요?

**A:** 가능하지만 인덱스 설계에 주의해야 합니다:

```java
// 부분 키 검색 DTO
public class PartialKeySearchDTO {
    @SearchableField(operators = {EQUALS})
    private String tenantId;  // 복합 키의 일부만 검색
    
    @SearchableField(operators = {CONTAINS})
    private String name;
}

// 인덱스 최적화 필요
CREATE INDEX idx_partial_tenant_name ON entity_table(tenant_id, name);
```

## 에러 처리

### Q: "Repository must implement JpaSpecificationExecutor" 에러가 발생해요.

**A:** 레포지토리에 `JpaSpecificationExecutor`를 추가해야 합니다:

```java
// 잘못된 예
public interface PostRepository extends JpaRepository<Post, Long> {
}

// 올바른 예
public interface PostRepository extends JpaRepository<Post, Long>, JpaSpecificationExecutor<Post> {
}
```

### Q: "Unable to determine ID field name" 에러가 발생해요.

**A:** 엔티티에 `@Id` 어노테이션이 없거나 복합 키 설정이 잘못되었을 수 있습니다:

```java
// 엔티티 ID 설정 예제는 기본 사용법 문서 참조
// 복합 키 엔티티 설정 예제는 고급 기능 문서 참조
```

### Q: SQL Server에서 "Incorrect syntax near ','" 에러가 발생해요.

**A:** 이는 복합 키 COUNT 쿼리 문제로, 자동으로 해결됩니다. 최신 버전을 사용하세요:

```yaml
# 로그로 확인
logging:
  level:
    dev.simplecore.searchable.core.service.specification.TwoPhaseQueryExecutor: DEBUG
```

## 고급 사용법

### Q: 커스텀 연산자를 만들 수 있나요?

**A:** 현재 Searchable JPA는 enum 기반의 SearchOperator를 사용하므로, 기본 제공되는 연산자만 사용할 수 있습니다. 커스텀 연산자가 필요한 경우 다음과 같은 방법으로 확장할 수 있습니다:

1. **기본 연산자 사용**: 제공되는 연산자로 대부분의 검색 요구사항을 충족할 수 있습니다
2. **커스텀 서비스 로직**: 복잡한 검색 로직은 서비스 레이어에서 별도 구현
3. **데이터베이스 함수**: 데이터베이스의 내장 함수를 활용한 검색

```java
// 권장: 기본 연산자 사용
@SearchableField(operators = {CONTAINS, STARTS_WITH, ENDS_WITH})
private String title;

// 또는 서비스 레이어에서 별도 처리
@Service
public class AdvancedSearchService {

    public Page<Post> searchWithCustomLogic(String query, Pageable pageable) {
        // 복잡한 검색 로직 구현
        if (query.startsWith("fulltext:")) {
            return performFullTextSearch(query.substring(9), pageable);
        }
        // 일반 검색
        return performRegularSearch(query, pageable);
    }
}
```

### Q: 프로젝션을 사용할 수 있나요?

**A:** 인터페이스 기반 프로젝션을 지원합니다:

```java
public interface PostSummary {
    String getTitle();
    String getAuthorName();
    LocalDateTime getCreatedAt();
}

// 사용
Page<PostSummary> summaries = postService.findAllWithSearch(condition, PostSummary.class);
```

### Q: 배치 업데이트/삭제는 어떻게 하나요?

**A:** 검색 조건 기반으로 배치 작업이 가능합니다:

```java
// 배치 업데이트
PostUpdateDTO updateData = new PostUpdateDTO();
updateData.setStatus(PostStatus.PUBLISHED);
long updatedCount = postService.updateWithSearch(searchCondition, updateData);

// 배치 삭제
long deletedCount = postService.deleteWithSearch(searchCondition);
```

## 모니터링 및 디버깅

### Q: 실행되는 쿼리를 확인하고 싶어요.

**A:** 로그 레벨을 조정하여 확인할 수 있습니다:

```yaml
logging:
  level:
    # Searchable JPA 로그
    dev.simplecore.searchable: DEBUG
    
    # Hibernate SQL 로그
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    
    # Spring Data JPA 로그
    org.springframework.data.jpa: DEBUG
```

### Q: 성능 메트릭을 수집하고 싶어요.

**A:** 로그 레벨을 조정하여 성능 정보를 확인할 수 있습니다:

```yaml
logging:
  level:
    dev.simplecore.searchable.core.service.specification.TwoPhaseQueryExecutor: DEBUG
```

또는 AOP를 사용하여 성능 모니터링을 구현할 수 있습니다:

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

## 마이그레이션

### Q: 기존 JPA 코드에서 마이그레이션하려면?

**A:** 점진적 마이그레이션이 가능합니다:

1. **1단계**: 의존성 추가 및 자동 설정 활성화
2. **2단계**: 기존 서비스를 `DefaultSearchableService`로 변경
3. **3단계**: 검색 DTO 정의 및 컨트롤러 수정
4. **4단계**: 성능 모니터링 및 최적화

```java
// 기존 코드와 병행 사용 가능
// 서비스 마이그레이션 예제는 기본 사용법 문서 참조
```

---

더 궁금한 점이 있으시면 [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues)에 문의해주세요.

---

[메인으로](../../README.md) | [문서 홈](README.md) | [이전: API 레퍼런스](api-reference.md) 