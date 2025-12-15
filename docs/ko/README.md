[English](../../README.md) | 한국어

# Searchable JPA

[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2%2B-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-SCL--1.0-blue.svg)](../../LICENSE)

Spring Data JPA를 확장하여 동적 검색, 정렬, 페이지네이션 기능을 제공하는 라이브러리입니다.

> [!TIP]
> `spring-boot-starter-searchable-jpa` 하나로 모든 기능을 사용할 수 있습니다!

## 주요 기능

| 기능 | 설명 |
|------|------|
| **동적 검색** | 18개 검색 연산자 지원 (EQUALS, CONTAINS, BETWEEN 등) |
| **유연한 정렬** | 다중 필드 정렬 및 동적 정렬 조건 |
| **고성능 페이지네이션** | 대용량 데이터셋을 위한 커서 기반 페이지네이션 |
| **타입 안전성** | 컴파일 타임 검증과 타입 안전 빌더 패턴 |
| **OpenAPI 통합** | Swagger 문서 자동 생성 |
| **다양한 데이터 타입** | 문자열, 숫자, 날짜, 열거형, 중첩 객체 지원 |

## 빠른 시작

### 1. 의존성 추가

```gradle
dependencies {
    implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:${version}'
}
```

### 2. GitHub Packages 저장소 설정

Searchable JPA는 GitHub Packages에 배포됩니다. `settings.gradle`에 다음을 추가하세요:

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

> [!IMPORTANT]
> GitHub 토큰에 `read:packages` 권한이 필요합니다. [GitHub Settings > Developer settings > Personal access tokens](https://github.com/settings/tokens)에서 생성하세요.

### 3. 기본 설정 (선택사항)

```yaml
searchable:
  swagger:
    enabled: true                    # OpenAPI 문서 자동 생성 (기본값: true)
  hibernate:
    auto-optimization: true          # Hibernate 최적화 자동 설정 (기본값: true)
    default-batch-fetch-size: 100    # 지연 로딩 배치 크기 (기본값: 100)
```

### 4. DTO 클래스 정의

```java
public class PostSearchDTO {
    @SearchableField(operators = {EQUALS, CONTAINS}, sortable = true)
    private String title;

    @SearchableField(operators = {EQUALS}, sortable = true)
    private PostStatus status;

    @SearchableField(operators = {GREATER_THAN, LESS_THAN}, sortable = true)
    private LocalDateTime createdAt;
}
```

### 5. 서비스 클래스 구현

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### 6. 컨트롤러에서 사용

```java
@RestController
public class PostController {
    @GetMapping("/api/posts/search")
    public Page<Post> searchPosts(
        @RequestParam @SearchableParams(PostSearchDTO.class) Map<String, String> params
    ) {
        SearchCondition<PostSearchDTO> condition =
            new SearchableParamsParser<>(PostSearchDTO.class).convert(params);
        return postService.findAllWithSearch(condition);
    }
}
```

### 7. API 호출

```bash
# 제목에 "Spring"이 포함된 게시물 검색
GET /api/posts/search?title.contains=Spring&sort=createdAt,desc&page=0&size=10
```

## 모듈 아키텍처

```
Searchable JPA
|
+-- searchable-jpa-core ------------ 핵심 라이브러리
|   |
|   +-- searchable-jpa-openapi ----- OpenAPI/Swagger 지원
|
+-- spring-boot-starter-searchable-jpa - Spring Boot 자동 설정 스타터
```

## 지원하는 검색 연산자

| 카테고리 | 연산자 |
|----------|--------|
| **비교** | equals, notEquals, greaterThan, greaterThanOrEqualTo, lessThan, lessThanOrEqualTo |
| **문자열** | contains, notContains, startsWith, notStartsWith, endsWith, notEndsWith |
| **범위** | between, notBetween |
| **컬렉션** | in, notIn |
| **NULL 체크** | isNull, isNotNull |

상세한 연산자 목록과 사용법은 [검색 연산자](./search-operators.md) 문서를 참조하세요.

## 버전 호환성

| 라이브러리 버전 | Spring Boot 버전 | Jakarta EE | 상태 |
|----------------|------------------|------------|------|
| `1.0.0+` | `3.2.x+` | jakarta.* | 최신 |
| `0.1.x` | `2.7.x` | javax.* | Deprecated |

## 튜토리얼

| 가이드 | 설명 |
|--------|------|
| [설치 가이드](./installation.md) | 시스템 요구사항 및 설치 안내 |
| [기본 사용법](./basic-usage.md) | 기본 사용법과 예제 |
| [검색 연산자](./search-operators.md) | 지원하는 모든 검색 연산자 |
| [2단계 쿼리 최적화](./two-phase-query-optimization.md) | 고성능 커서 기반 페이지네이션 |
| [관계형 데이터와 2단계 쿼리](./relationship-and-two-phase-query.md) | JPA 관계 매핑과 N+1 문제 해결 |
| [고급 기능](./advanced-features.md) | 복잡한 검색 조건과 고급 기능 |
| [OpenAPI 통합](./openapi-integration.md) | Swagger 문서 자동 생성 |
| [API 레퍼런스](./api-reference.md) | 전체 API 문서 |
| [자주 묻는 질문](./faq.md) | FAQ 및 문제 해결 |

## 요구 사항

- Java 17+
- Spring Boot 3.2.x+
- Gradle 8.5+

## 라이선스

이 프로젝트는 [SimpleCORE License 1.0 (SCL-1.0)](./license.md) 라이선스를 따릅니다.

## 개발팀

Searchable JPA는 [SimpleCORE Inc.](https://simplecore.kr)에서 개발하고 있습니다.

- **Website**: [simplecore.kr](https://simplecore.kr)
- **GitHub**: [github.com/simplecore-inc](https://github.com/simplecore-inc)
- **Contact**: license@simplecore.kr
