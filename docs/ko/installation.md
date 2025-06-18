# 설치 가이드

[메인으로](../../README.md) | [문서 홈](README.md) | [다음: 기본 사용법](basic-usage.md)

---

이 문서는 Searchable JPA를 프로젝트에 설치하고 설정하는 방법을 설명합니다.

## 시스템 요구사항

- Java 8 이상
- Spring Boot 2.7.x 이상
- Spring Data JPA

## 의존성 추가

### Gradle

```gradle
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:0.0.4-SNAPSHOT'
}
```

### Maven

```xml
<dependency>
    <groupId>dev.simplecore.searchable</groupId>
    <artifactId>spring-boot-starter-searchable-jpa</artifactId>
    <version>0.0.4-SNAPSHOT</version>
</dependency>
```

## 개별 모듈 설치

필요에 따라 개별 모듈을 선택적으로 설치할 수 있습니다.

### Core 모듈만 사용

```gradle
dependencies {
    implementation 'dev.simplecore.searchable:searchable-jpa-core:0.0.4-SNAPSHOT'
}
```

### OpenAPI 지원 추가

```gradle
dependencies {
    implementation 'dev.simplecore.searchable:searchable-jpa-core:0.0.4-SNAPSHOT'
    implementation 'dev.simplecore.searchable:searchable-jpa-openapi:0.0.4-SNAPSHOT'
}
```

## Spring Boot 설정

### 자동 설정 (권장)

Spring Boot Starter를 사용하면 별도의 설정 없이 자동으로 구성됩니다.

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

### 수동 설정

필요한 경우 수동으로 설정할 수 있습니다.

```java
@Configuration
@EnableJpaRepositories
public class SearchableJpaConfig {
    
    @Bean
    public SearchableService<?> searchableService(JpaRepository<?, ?> repository) {
        return new DefaultSearchableService<>(repository, YourEntity.class);
    }
}
```

## application.yml 설정

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        
# Searchable JPA 설정 (선택사항)
searchable:
  jpa:
    default-page-size: 20
    max-page-size: 100
```

## 설정 속성

| 속성 | 기본값 | 설명 |
|------|-------|------|
| `searchable.jpa.default-page-size` | 20 | 기본 페이지 크기 |
| `searchable.jpa.max-page-size` | 100 | 최대 페이지 크기 |
| `searchable.jpa.enable-cursor-pagination` | true | 커서 기반 페이징 활성화 |

## 버전 호환성

| Searchable JPA | Spring Boot | Java |
|----------------|-------------|------|
| 0.0.4-SNAPSHOT | 2.7.x | 8+ |
| 0.0.3 | 2.7.x | 8+ |

## 문제 해결

### 일반적인 문제들

#### 1. 의존성 충돌

```
Caused by: java.lang.NoClassDefFoundError: org/springframework/data/jpa/repository/JpaSpecificationExecutor
```

**해결방법**: Spring Data JPA 의존성이 누락되었습니다.

```gradle
implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
```

#### 2. 자동 설정이 작동하지 않음

**해결방법**: 메인 클래스에 `@SpringBootApplication` 어노테이션이 있는지 확인하세요.

#### 3. Repository를 찾을 수 없음

```
No qualifying bean of type 'org.springframework.data.jpa.repository.JpaRepository'
```

**해결방법**: JPA Repository가 올바르게 정의되어 있는지 확인하세요.

```java
@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
}
```

### 로그 설정

디버깅을 위한 로그 레벨 설정:

```yaml
logging:
  level:
    dev.simplecore.searchable: DEBUG
    org.springframework.data.jpa: DEBUG
```

## 다음 단계

설치가 완료되었다면 [기본 사용법](basic-usage.md)을 참조하여 첫 번째 검색 기능을 구현해보세요.

---

[메인으로](../../README.md) | [문서 홈](README.md) | [다음: 기본 사용법](basic-usage.md) 