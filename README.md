# Searchable JPA

[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.oracle.com/java/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.7%2B-green.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Searchable JPA는 Spring Data JPA를 확장하여 동적 검색, 정렬, 페이징 기능을 제공하는 라이브러리입니다. 복잡한 검색 조건을 간단한 어노테이션과 빌더 패턴으로 구현할 수 있으며, 대용량 데이터에서도 높은 성능을 보장하는 커서 기반 페이징을 지원합니다.

## 주요 기능

- **동적 검색**: 20개 이상의 검색 연산자 지원 (EQUALS, CONTAINS, BETWEEN 등)
- **유연한 정렬**: 다중 필드 정렬과 동적 정렬 조건
- **고성능 페이징**: 커서 기반 페이징으로 대용량 데이터 처리
- **타입 안전성**: 컴파일 타임 검증과 타입 안전한 빌더 패턴
- **OpenAPI 통합**: Swagger 문서 자동 생성
- **다양한 데이터 타입**: 문자열, 숫자, 날짜, Enum, 중첩 객체 지원

## 빠른 시작

### 1. 의존성 추가

```gradle
implementation 'dev.simplecore.searchable:spring-boot-starter-searchable-jpa:0.0.4-SNAPSHOT'
```

### 2. DTO 클래스 정의

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

### 3. 서비스 클래스 구현

```java
@Service
public class PostService extends DefaultSearchableService<Post, Long> {
    public PostService(PostRepository repository, EntityManager entityManager) {
        super(repository, entityManager);
    }
}
```

### 4. 컨트롤러에서 사용

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

### 5. API 호출

```bash
# 제목에 "Spring"이 포함된 게시글 검색
GET /api/posts/search?title.contains=Spring&sort=createdAt,desc&page=0&size=10
```

## 문서

### 한국어 문서
- [설치 가이드](docs/ko/installation.md) - 시스템 요구사항 및 설치 방법
- [기본 사용법](docs/ko/basic-usage.md) - 기본적인 사용 방법과 예제
- [검색 연산자](docs/ko/search-operators.md) - 지원하는 모든 검색 연산자
- [고급 기능](docs/ko/advanced-features.md) - 복잡한 검색 조건과 고급 기능
- [커서 페이징](docs/ko/cursor-pagination.md) - 고성능 커서 기반 페이징
- [OpenAPI 통합](docs/ko/openapi-integration.md) - Swagger 문서 자동 생성
- [API 레퍼런스](docs/ko/api-reference.md) - 전체 API 문서
- [FAQ](docs/ko/faq.md) - 자주 묻는 질문과 문제 해결

### English Documentation
- [Installation Guide](docs/en/installation.md) - System requirements and installation
- [Basic Usage](docs/en/basic-usage.md) - Basic usage patterns and examples
- [Search Operators](docs/en/search-operators.md) - All supported search operators
- [Advanced Features](docs/en/advanced-features.md) - Complex search conditions and advanced features
- [Cursor Pagination](docs/en/cursor-pagination.md) - High-performance cursor-based pagination
- [OpenAPI Integration](docs/en/openapi-integration.md) - Automatic Swagger documentation
- [API Reference](docs/en/api-reference.md) - Complete API documentation
- [FAQ](docs/en/faq.md) - Frequently asked questions and troubleshooting

## 시스템 요구사항

- **Java**: 8 이상
- **Spring Boot**: 2.7.x 이상
- **Spring Data JPA**: 2.7.x 이상
- **데이터베이스**: MySQL, PostgreSQL, H2 등 JPA 지원 DB

## 라이선스

이 프로젝트는 [Apache License 2.0](LICENSE) 하에 배포됩니다.

## 기여

프로젝트에 기여하고 싶으시다면:

1. 이 저장소를 포크하세요
2. 기능 브랜치를 생성하세요 (`git checkout -b feature/amazing-feature`)
3. 변경사항을 커밋하세요 (`git commit -m 'Add amazing feature'`)
4. 브랜치에 푸시하세요 (`git push origin feature/amazing-feature`)
5. Pull Request를 생성하세요

## 지원

- **이슈**: [GitHub Issues](https://github.com/simplecore-inc/searchable-jpa/issues)
- **문서**: [Documentation](docs/ko/README.md)

---

**Searchable JPA**로 더 쉽고 빠른 검색 기능을 구현해보세요! 